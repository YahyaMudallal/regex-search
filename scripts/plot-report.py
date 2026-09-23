#!/usr/bin/env python3
"""Compare DFA, DFAM, KMP (littéraux seulement) et GNU grep depuis les CSV validés."""
import argparse
import json
import os
from pathlib import Path
import statistics
import sys
import textwrap

ROOT = Path(__file__).resolve().parents[1]
os.environ.setdefault('MPLCONFIGDIR', str(ROOT / '.cache/matplotlib'))
os.environ.setdefault('XDG_CACHE_HOME', str(ROOT / '.cache'))
sys.path.insert(0, str(ROOT / 'scripts/lib'))
from fixed_report import load_validated_results
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import numpy as np

COLORS = {'KMP': '#233b63', 'DFA': '#bc6b22', 'DFAM': '#8654a3', 'grep': '#137e72'}
NAMES = {'KMP': 'KMP', 'DFA': 'DFA', 'DFAM': 'DFAM · Hopcroft', 'grep': 'GNU grep -E (egrep)'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('campaign', type=Path, nargs='?', default=ROOT/'target/report/results')
    parser.add_argument('--output', type=Path, default=ROOT/'target/report/figures')
    args = parser.parse_args()
    if args.output.resolve().is_relative_to((ROOT/'docs/assets').resolve()):
        parser.error('Tracer localement puis publier avec freeze-report.sh ou report-campaign.sh.')
    manifest, cli, jvm, cli_summaries, jvm_summaries = load_validated_results(args.campaign)
    profile = manifest['profile']
    groups = {}
    for case in profile['experiments']:
        groups.setdefault(case['comparison'], {})[case['strategy']] = case
    cs = {(r['case_id'],r['engine']):r for r in cli_summaries}
    js = {(r['case_id'],r['metric']):r for r in jvm_summaries}
    args.output.mkdir(parents=True, exist_ok=True)
    plt.rcParams.update({'font.family':'DejaVu Sans', 'font.size':10, 'text.color':'#233249',
        'axes.spines.top':False, 'axes.spines.right':False, 'axes.edgecolor':'#bdc7d2',
        'figure.facecolor':'white', 'axes.facecolor':'white', 'svg.hashsalt':profile['protocol'],
        'svg.fonttype':'none', 'text.parse_math':False})
    note = f"{profile['protocol']} · {manifest['finished_utc'][:10]} · profil {manifest['profile_sha256'][:10]}"

    def case(key):
        return groups[key]['DFA']

    def label(key):
        return '\n'.join(textwrap.wrap(case(key)['regex'], width=48, break_on_hyphens=False))

    def row(key, engine):
        c = groups[key]['DFA' if engine == 'grep' else engine]
        return cs[c['id'], 'grep' if engine == 'grep' else 'java']

    def save(fig, name, keys):
        fig.text(.01,.008,note,fontsize=8,color='#526477')
        metadata={'Title':name,'Description':json.dumps({k:case(k)['regex'] for k in keys},ensure_ascii=False)}
        svg = args.output/f'{name}.svg'
        fig.savefig(svg,bbox_inches='tight',metadata={**metadata,'Date':manifest['finished_utc']})
        # L'alphabet du moteur ne dépend d'aucun encodage de texte. La déclaration XML
        # générée par Matplotlib est inutile pour nos SVG ASCII et est retirée avant hachage.
        raw = svg.read_bytes()
        if raw.startswith(b'<?xml '):
            newline = raw.find(b'\n')
            if newline >= 0:
                svg.write_bytes(raw[newline + 1:])
        fig.savefig(args.output/f'{name}.png',bbox_inches='tight',dpi=145,metadata=metadata)
        plt.close(fig)

    def bars(keys, name, title):
        fig,ax=plt.subplots(figsize=(15,3+len(keys)*1.05))
        for engine,shift in zip(('KMP','DFA','DFAM','grep'),(-.27,-.09,.09,.27)):
            selected=[(i,k) for i,k in enumerate(keys) if engine=='grep' or engine in groups[k]]
            stats=[row(k,engine) for _,k in selected]
            med=np.array([r['median_ms'] for r in stats])
            err=[[r['median_ms']-r['q25_ms'] for r in stats],[r['q75_ms']-r['median_ms'] for r in stats]]
            rectangles=ax.barh([i+shift for i,_ in selected],med,height=.16,xerr=err,capsize=2,
                               color=COLORS[engine],label=NAMES[engine])
            for rect,r in zip(rectangles,stats):
                ax.annotate(f"{r['median_ms']:.1f}"+(' *' if r['noisy'] else ''),
                    (r['q75_ms'],rect.get_y()+rect.get_height()/2),xytext=(5,0),textcoords='offset points',va='center',fontsize=8)
        ax.set_yticks(range(len(keys)),[label(k) for k in keys],fontsize=9)
        ax.invert_yaxis(); ax.set_xlim(0,ax.get_xlim()[1]*1.12)
        ax.set_xlabel('Commande complète (ms) · médiane et IQR de 30 processus · démarrage JVM inclus')
        ax.grid(axis='x',alpha=.15);ax.set_axisbelow(True)
        ax.legend(loc='upper center',bbox_to_anchor=(.5,-.11),ncol=4,frameon=False,fontsize=9)
        ax.set_title('Regex exacte à gauche · KMP absent = non applicable aux opérateurs regex\n* IQR/médiane > 15 % · aucun point retiré · grep : série associée au cas DFA',loc='left',fontsize=10,pad=14)
        fig.suptitle(title,fontsize=17,weight='bold',x=.02,ha='left')
        fig.tight_layout(rect=(0,.10,1,.94));save(fig,name,keys)

    book=[k for k in groups if case(k)['group']=='book']
    bars(book,'latency','DFA, DFAM, KMP et egrep · même corpus et même motif')
    stress=[k for k in groups if case(k)['group'] in {'stress','growth'} and case(k).get('cli',True)]
    bars(stress,'stress','Préfixes répétés et croissance du DFA · corpus synthétique')

    fig,axes=plt.subplots(1,2,figsize=(15,7))
    allkeys=[]
    for ax,keys in zip(axes,[['literal','scale-literal-8','scale-literal-32'],['complex','scale-complex-8','scale-complex-32']]):
        allkeys+=keys
        sizes=[manifest['corpora'][case(k)['corpus']]['bytes']/1024**2 for k in keys]
        for engine in ('KMP','DFA','DFAM','grep'):
            if engine!='grep' and engine not in groups[keys[0]]:continue
            stats=[row(k,engine) for k in keys]
            ax.errorbar(sizes,[r['median_ms'] for r in stats],
                yerr=[[r['median_ms']-r['q25_ms'] for r in stats],[r['q75_ms']-r['median_ms'] for r in stats]],
                fmt='o-',capsize=3,color=COLORS[engine],label=NAMES[engine])
        ax.set(xlabel='Corpus répété ×1, ×8, ×32 (Mio)',ylabel='Commande complète (ms)',xlim=(0,None),ylim=(0,None))
        ax.set_title('Regex exacte :\n'+label(keys[0]),fontsize=9,loc='left',pad=12)
        ax.grid(alpha=.15);ax.legend(frameon=False,fontsize=8)
    fig.suptitle('Effet du volume · médiane et IQR de 30 processus',fontsize=16,weight='bold',x=.04,ha='left')
    fig.text(.04,.89,'KMP comparé sur Elizabeth seulement · les segments entre trois volumes ne prouvent pas une complexité',fontsize=10)
    fig.tight_layout(rect=(0,.05,1,.83));save(fig,'scaling',allkeys)

    fig,axes=plt.subplots(1,4,figsize=(16,5))
    for ax,engine in zip(axes,('KMP','DFA','DFAM','grep')):
        identifier=groups['literal']['DFA' if engine=='grep' else engine]['id']
        values=[r['elapsed_ns']/1e6 for r in cli if r['case_id']==identifier and r['phase']=='measure'
                and r['engine']==('grep' if engine=='grep' else 'java')]
        stats=row('literal',engine)
        ax.plot(range(1,len(values)+1),values,'o-',markersize=3,linewidth=.8,color=COLORS[engine])
        ax.axhspan(stats['q25_ms'],stats['q75_ms'],color=COLORS[engine],alpha=.12,label='IQR')
        ax.axhline(stats['median_ms'],color='#233249',linestyle='--',label='Médiane')
        ax.set(title=NAMES[engine],xlabel="Ordre d'exécution",ylabel='Processus complet (ms)',ylim=(0,None))
        ax.legend(fontsize=8,frameon=False);ax.grid(alpha=.15)
    fig.suptitle('Toutes les observations · regex exacte : Elizabeth',fontsize=16,weight='bold',x=.04,ha='left')
    fig.text(.04,.89,'30 processus par moteur · axes verticaux propres à chaque panneau · aucun point retiré',fontsize=10)
    fig.tight_layout(rect=(0,.05,1,.84));save(fig,'distribution',['literal'])

    fig,axes=plt.subplots(1,4,figsize=(19,10),sharey=True)
    for ax,metric,title in zip(axes,('minimization_ns','preparation_ns','scan_ns','total_ns'),('Hopcroft seul','Préparation totale','Lecture + recherche','Pipeline complet')):
        for engine,shift in zip(('KMP','DFA','DFAM'),(-.2,0,.2)):
            for i,key in enumerate(book):
                if engine not in groups[key]:continue
                c=groups[key][engine];r=js[c['id'],metric]
                means=[statistics.mean(v[metric]/1e6 for v in jvm if v['case_id']==c['id'] and v['fork']==fork and v['phase']=='measure') for fork in range(1,profile['jvm']['forks']+1)]
                ax.plot([r['min_ms'],r['max_ms']],[i+shift]*2,color=COLORS[engine],alpha=.5)
                ax.scatter(means,[i+shift]*len(means),s=16,color=COLORS[engine],alpha=.7)
                ax.scatter([r['median_ms']],[i+shift],color='#233249',marker='|',s=100)
            ax.plot([],[], 'o',color=COLORS[engine],label=NAMES[engine])
        ax.set(title=title,xlabel='ms · moyennes de JVM',xlim=(-.001,None));ax.locator_params(axis='x',nbins=4)
        ax.grid(axis='x',alpha=.15)
    axes[0].set_yticks(range(len(book)),[label(k) for k in book],fontsize=9);axes[0].invert_yaxis()
    handles,names=axes[-1].get_legend_handles_labels()
    fig.legend(handles,names,loc='lower center',bbox_to_anchor=(.65,.035),ncol=3,frameon=False,fontsize=9)
    fig.suptitle('Coût de la minimisation et du parcours · DFA / DFAM / KMP',fontsize=17,weight='bold',x=.02,ha='left')
    fig.text(.02,.915,'5 JVM par cas · 10 prépassages puis 10 mesures · point = moyenne de JVM ; trait noir = médiane ; segment = min–max\nMême préparation reconstruite à chaque invocation · IO incluses · Hopcroft vaut zéro en DFA/KMP et pour le raccourci nullable',fontsize=10)
    fig.tight_layout(rect=(0,.05,1,.87));save(fig,'phases',book)

    growth=[k for k in groups if case(k)['group']=='growth']
    fig,axes=plt.subplots(1,2,figsize=(14,7))
    depths=[case(k)['branch_depth'] for k in growth]
    for metric,name,color in [('nfa_states','NFA','#526477'),('search_dfa_states','DFA',COLORS['DFA']),('dfam_states','DFAM',COLORS['DFAM'])]:
        values=[manifest['automata'][case(k)['id']][metric] for k in growth]
        axes[0].plot(depths,values,'o-',color=color,label=name)
        for x,y in zip(depths,values):axes[0].annotate(str(y),(x,y),xytext=(3,4 if metric!='dfam_states' else -13),textcoords='offset points',fontsize=8)
    axes[0].set(xlabel='Nombre d de blocs (a|b)',ylabel="Nombre d'états")
    for engine,metric,name,color in [('DFA','preparation_ns','Préparation DFA',COLORS['DFA']),('DFAM','preparation_ns','Préparation DFAM',COLORS['DFAM']),('DFAM','minimization_ns','Hopcroft seul','#233b63')]:
        stats=[js[groups[k][engine]['id'],metric] for k in growth]
        axes[1].errorbar(depths,[r['median_ms'] for r in stats],
            yerr=[[r['median_ms']-r['min_ms'] for r in stats],[r['max_ms']-r['median_ms'] for r in stats]],fmt='o-',capsize=3,color=color,label=name)
    axes[1].set(xlabel='Nombre d de blocs (a|b)',ylabel='Temps (ms)',ylim=(0,None))
    for ax in axes:ax.set_xticks(depths);ax.grid(alpha=.15);ax.legend(frameon=False)
    fig.suptitle('Croissance du DFA et effet de Hopcroft',fontsize=17,weight='bold',x=.04,ha='left')
    fig.text(.04,.88,'Regex exécutée : (a|b)*a, puis d copies de (a|b), puis b ; d = 5, 7, 9\nProfil structurel hors chronométrage · médiane et min–max des 5 moyennes de JVM',fontsize=10)
    fig.tight_layout(rect=(0,.05,1,.82));save(fig,'compilation',growth)
    print(f'Six figures comparatives SVG et PNG créées dans {args.output}')


if __name__=='__main__':
    main()
