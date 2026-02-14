#version 460

in vec4 vColor;
out vec4 fragColor;

void main() {
    // ★ デバッグ：色を無視して法線 or 位置で塗るテスト

    // 1 vColor をそのまま出す（今の状態）
    fragColor = vColor;

    // もしまだ変なら、こういうのも試せる：
    // fragColor = vec4(1.0, 0.0, 0.0, 1.0); // 全面まっ赤になるか？
}
