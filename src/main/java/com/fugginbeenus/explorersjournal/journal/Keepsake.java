package com.fugginbeenus.explorersjournal.journal;

/** A thing taped into the journal. The whole item is kept as text, so nothing about it is lost. x and y are its top left corner on the page. page counts from the entry's first page. note is the short line written beside it, and may be missing in older journals. */
public final class Keepsake {
    public String id;
    public String item;
    public int page;
    public int x, y;
    public String note;
}
