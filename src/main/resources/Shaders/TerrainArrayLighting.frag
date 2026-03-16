#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform sampler2DArray m_DiffuseMapArray;
uniform vec4 m_SeamMaskColor;
uniform float m_SeamMaskStrength;

varying vec3 texCoord;
varying vec3 lightColor;
varying float seamWeight;

void main() {
    vec4 diffuseColor = texture2DArray(m_DiffuseMapArray, texCoord);
    vec3 litColor = diffuseColor.rgb * lightColor;
    float seamMaskAmount = clamp(seamWeight * m_SeamMaskStrength, 0.0, 1.0);
    litColor = mix(litColor, m_SeamMaskColor.rgb, seamMaskAmount);
    gl_FragColor = vec4(litColor, diffuseColor.a);
}
