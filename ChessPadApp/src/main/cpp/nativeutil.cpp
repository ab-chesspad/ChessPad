/*
    DroidFish - An Android chess program.
    Copyright (C) 2011-2012  Peter Österlund, peterosterlund2@gmail.com

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

     Alexander Bootman modified for ChessPad 03/08/2020
*/

#include <jni.h>
#include <unistd.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/resource.h>

/*
 * Class:     org_petero_droidfish_engine_EngineUtil
 * Method:    chmod
 * Signature: (Ljava/lang/String;I)Z
 */
extern "C"
JNIEXPORT jboolean JNICALL
Java_org_petero_droidfish_engine_UCIEngine_chmod(JNIEnv *env, jclass clazz, jstring exe_path, jint mod) {
    const char *exePath = env->GetStringUTFChars(exe_path, NULL);
    if (!exePath)
        return (jboolean) false;
    bool ret = chmod(exePath, static_cast<mode_t>(mod)) == 0;
    env->ReleaseStringUTFChars(exe_path, exePath);
    return (jboolean) ret;
}

/*
 * Class:     org_petero_droidfish_engine_EngineUtil
 * Method:    reNice
 * Signature: (II)V
 */
extern "C"
JNIEXPORT void JNICALL
Java_org_petero_droidfish_engine_UCIEngine_reNice(JNIEnv *env, jclass clazz, jint pid, jint prio) {
    setpriority(PRIO_PROCESS, static_cast<id_t>(pid), prio);
}
