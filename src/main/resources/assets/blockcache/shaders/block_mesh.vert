#version 460

layout(location = 0) in vec4 inPos;    // compute shader が書いた頂点位置
layout(location = 1) in vec4 inNormal; // 今は使わないけど VAO にある
layout(location = 2) in vec4 inColor;  // compute shader が書いた色

out vec4 vColor;

uniform mat4 uProjection;
uniform mat4 uView;
uniform mat4 uModel;

void main() {
    vColor = inColor;

    // 3D メッシュとして描画する
    gl_Position = uProjection * uView * uModel * inPos;
}
