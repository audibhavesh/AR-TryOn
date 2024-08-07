#version 300 es
uniform mat4 u_ModelViewProjection;
in vec3 a_Position;
in vec2 a_TexCoord;
out vec2 v_TexCoord;

void main() {
    gl_Position = u_ModelViewProjection * vec4(a_Position, 1.0);
    v_TexCoord = a_TexCoord;
}
