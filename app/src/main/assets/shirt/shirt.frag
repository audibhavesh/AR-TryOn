#version 300 es
precision mediump float;
uniform sampler2D u_AlbedoTexture;
in vec2 v_TexCoord;
out vec4 fragColor;

void main() {
    fragColor = texture(u_AlbedoTexture, v_TexCoord);
}
