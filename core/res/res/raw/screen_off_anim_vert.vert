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

uniform mat4 proj_matrix;
uniform mat4 tex_matrix;
uniform float anim_style;
uniform float scale;
uniform float screen_width;
uniform float screen_height;
attribute vec2 position;
attribute vec2 uv;
varying vec2 UV;
varying vec2 fragPos;

void main()
{
    vec4 transformed_uv = tex_matrix * vec4(uv.x, uv.y, 1.0, 1.0);
    UV = transformed_uv.st / transformed_uv.q;

    vec2 center = vec2(screen_width * 0.5, screen_height * 0.5);
    vec2 pos = position;

    if (anim_style < 1.5) {
        pos.y = center.y + (pos.y - center.y) * scale;
    } else if (anim_style < 2.5) {
        pos = center + (pos - center) * scale;
    } else if (anim_style < 4.5 && anim_style > 3.5) {
        pos.y = center.y + (pos.y - center.y) * scale;
    }

    fragPos = pos / vec2(screen_width, screen_height);
    gl_Position = proj_matrix * vec4(pos.x, pos.y, 0.0, 1.0);
}
