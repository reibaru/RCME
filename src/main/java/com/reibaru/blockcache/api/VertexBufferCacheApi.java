package com.reibaru.blockcache.api;

import com.reibaru.blockcache.CachedMesh;

public interface VertexBufferCacheApi {

    void blockcache$uploadFromCachedMesh(CachedMesh mesh);

    boolean blockcache$isUsingCachedMesh();
    void blockcache$setUsingCachedMesh(boolean value);
}
