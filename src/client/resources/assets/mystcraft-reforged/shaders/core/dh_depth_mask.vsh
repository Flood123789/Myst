#version 150

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // The quad is drawn in 0..1 space under an orthographic projection, so the position doubles
    // as the sample coordinate for the full screen depth texture.
    texCoord = Position.xy;
}
