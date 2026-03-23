/*
 * Copyright (C) 2014-2026 The BlissRoms Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#extension GL_OES_EGL_image_external : require
precision mediump float;
uniform samplerExternalOES texUnit;
uniform float opacity;
uniform float gamma;
uniform float progress;
uniform float anim_style;
varying vec2 UV;
varying vec2 fragPos;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

void main()
{
    vec4 color = texture2D(texUnit, UV);

    if (anim_style < 1.5) {
        float distFromCenter = abs(fragPos.y - 0.5);
        if (progress > 0.7) {
            float linePhase = (progress - 0.7) / 0.3;
            float lineWidth = 0.008 * (1.0 - linePhase);
            float horizontalShrink = 1.0 - linePhase;
            float distX = abs(fragPos.x - 0.5);
            if (distX < horizontalShrink * 0.5 && distFromCenter < lineWidth) {
                float glow = (1.0 - smoothstep(0.0, lineWidth, distFromCenter)) * (1.0 - linePhase);
                gl_FragColor = vec4(glow, glow, glow, 1.0);
            } else {
                gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
            }
            return;
        }
        vec3 rgb = pow(color.rgb * opacity, vec3(gamma));
        gl_FragColor = vec4(rgb, 1.0);

    } else if (anim_style < 5.5) {
        float dist = distance(fragPos, vec2(0.5));
        float maxDist = 0.7072;
        float radius = maxDist * (1.0 - progress);
        float edge = smoothstep(radius - 0.03, radius, dist);
        vec3 rgb = color.rgb * opacity * (1.0 - edge);
        gl_FragColor = vec4(rgb, 1.0);

    } else if (anim_style < 6.5) {
        vec2 blockPos = floor(fragPos * 40.0);
        float noise = hash(blockPos);
        float threshold = progress * 1.2;
        if (noise < threshold) {
            gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
        } else {
            float fade = 1.0 - smoothstep(threshold - 0.15, threshold, noise);
            gl_FragColor = vec4(color.rgb * (0.5 + 0.5 * fade), 1.0);
        }

    } else if (anim_style < 7.5) {
        float staticNoise = hash(fragPos * 500.0 + vec2(progress * 137.0));
        if (progress < 0.4) {
            float blend = progress / 0.4;
            vec3 mixed = mix(color.rgb, vec3(staticNoise), blend * 0.7);
            gl_FragColor = vec4(mixed, 1.0);
        } else {
            float fade = 1.0 - (progress - 0.4) / 0.6;
            gl_FragColor = vec4(vec3(staticNoise) * fade, 1.0);
        }

    } else if (anim_style < 8.5) {
        float blockSize = 1.0 + progress * 80.0;
        vec2 blockUV = floor(UV * blockSize) / blockSize;
        vec4 blockColor = texture2D(texUnit, blockUV);
        float fade = 1.0 - progress;
        gl_FragColor = vec4(blockColor.rgb * fade, 1.0);

    } else if (anim_style < 9.5) {
        float numBlinds = 8.0;
        float blindPos = fract(fragPos.y * numBlinds);
        float blindProgress = smoothstep(0.0, 1.0, progress * 1.3);
        float halfBlind = blindProgress * 0.5;
        if (blindPos < halfBlind || blindPos > (1.0 - halfBlind)) {
            gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
        } else {
            gl_FragColor = vec4(color.rgb * opacity, 1.0);
        }

    } else {
        vec3 rgb = pow(color.rgb * opacity, vec3(gamma));
        gl_FragColor = vec4(rgb, 1.0);
    }
}
