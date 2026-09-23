package com.sorbonne.search;

/** Motif préparé immuable, partageable ; chaque curseur possède son propre état. */
public interface PreparedSearch {
    boolean search(String text);

    SearchCursor newCursor();
}
