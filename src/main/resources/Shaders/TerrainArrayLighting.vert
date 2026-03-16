#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/Lighting.glsllib"
#import "Common/ShaderLib/BlinnPhongLighting.glsllib"

attribute vec3 inPosition;
attribute vec3 inNormal;
attribute vec3 inTexCoord;

varying vec3 texCoord;
varying vec3 lightColor;

uniform vec4 g_LightColor;
uniform vec4 g_LightPosition;
uniform vec4 g_LightDirection;
uniform vec4 g_AmbientLightColor;
uniform mat4 g_WorldViewProjectionMatrix;
uniform mat4 g_WorldViewMatrix;
uniform mat3 g_NormalMatrix;
uniform mat4 g_ViewMatrix;

void main() {
    vec4 modelSpacePos = vec4(inPosition, 1.0);
    gl_Position = g_WorldViewProjectionMatrix * modelSpacePos;
    texCoord = inTexCoord;

    vec3 wvPosition = (g_WorldViewMatrix * modelSpacePos).xyz;
    vec3 wvNormal = normalize(g_NormalMatrix * inNormal);
    vec3 viewDir = normalize(-wvPosition);

    vec4 wvLightPos = g_ViewMatrix * vec4(g_LightPosition.xyz, clamp(g_LightColor.w, 0.0, 1.0));
    wvLightPos.w = g_LightPosition.w;

    vec4 vLightDir;
    vec3 lightVec;
    lightComputeDir(wvPosition, g_LightColor.w, wvLightPos, vLightDir, lightVec);

    float spotFallOff = 1.0;
    #if __VERSION__ >= 110
        if (g_LightDirection.w != 0.0) {
            spotFallOff = computeSpotFalloff(g_LightDirection, lightVec);
        }
    #endif

    vec2 lighting = computeLighting(wvNormal, viewDir, vLightDir.xyz, vLightDir.w * spotFallOff, 1.0);
    lightColor = g_AmbientLightColor.rgb + (g_LightColor.rgb * lighting.x);
}
