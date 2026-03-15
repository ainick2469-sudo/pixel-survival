#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform sampler2DArray m_DiffuseMapArray;

varying vec3 texCoord;
varying vec3 lightColor;

void main() {
    vec4 diffuseColor = texture2DArray(m_DiffuseMapArray, texCoord);
    gl_FragColor = vec4(diffuseColor.rgb * lightColor, diffuseColor.a);
}
