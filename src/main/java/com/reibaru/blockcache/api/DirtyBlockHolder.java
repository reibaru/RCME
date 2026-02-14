package com.reibaru.blockcache.api;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

public interface DirtyBlockHolder {

    public IntOpenHashSet blockcache$getDirtyBlocks();

    void blockcache$clearDirtyBlocks();
}
