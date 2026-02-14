#version 460

layout(location = 0) in vec2 vUV;
layout(location = 0) out vec4 outColor;

uniform int tilesX;
uniform int tilesY;
uniform int screenWidth;
uniform int screenHeight;

uniform sampler2DArray texAtlas;

struct TilePixel {
    float texIndex;
    float u;
    float v;
    float depth;
};

layout(std430, binding = 3) buffer TileData {
    TilePixel pixels[];
};

int getTileIndex() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);

    int tileW = screenWidth  / tilesX;
    int tileH = screenHeight / tilesY;

    int tx = pixel.x / tileW;
    int ty = pixel.y / tileH;

    return ty * tilesX + tx;
}

void main() {
    int index = getTileIndex();
    TilePixel px = pixels[index];

    if (px.texIndex < 0.0) {
        fragColor = vec4(0,0,0,1);
        return;
    }

    int layer = int(px.texIndex);
    vec4 color = texture(texAtlas, vec3(px.u, px.v, layer));

    fragColor = color;
}
