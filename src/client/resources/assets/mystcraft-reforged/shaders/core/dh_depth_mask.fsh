#version 150

uniform sampler2D Sampler0;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    float lodDepth = texture(Sampler0, texCoord).r;

    // Untouched depth means Distant Horizons drew no LOD on this pixel, so leave the buffer alone.
    if (lodDepth >= 1.0) {
        discard;
    }

    fragColor = vec4(0.0);
    gl_FragDepth = lodDepth;
}
