#include <jni.h>
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#define VK_NO_PROTOTYPES
#include <vulkan/vulkan.h>

#include "log.h"

#include "utils.h"

typedef int (*Main_Function_t)(int, char**);
typedef void (*android_update_LD_LIBRARY_PATH_t)(char*);

JNIEXPORT jboolean JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_probeSystemVulkan(
        JNIEnv *env, jclass clazz) {
    void *vulkan = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    if (vulkan == NULL) {
        LOGW("Vulkan probe: libvulkan.so could not be loaded: %s", dlerror());
        return JNI_FALSE;
    }

    PFN_vkGetInstanceProcAddr get_instance_proc_addr =
            (PFN_vkGetInstanceProcAddr) dlsym(vulkan, "vkGetInstanceProcAddr");
    PFN_vkCreateInstance create_instance = get_instance_proc_addr == NULL ? NULL :
            (PFN_vkCreateInstance) get_instance_proc_addr(NULL, "vkCreateInstance");
    if (create_instance == NULL) {
        create_instance = (PFN_vkCreateInstance) dlsym(vulkan, "vkCreateInstance");
    }
    if (create_instance == NULL) {
        LOGW("Vulkan probe: vkCreateInstance is unavailable");
        dlclose(vulkan);
        return JNI_FALSE;
    }

    VkApplicationInfo app_info = {
            .sType = VK_STRUCTURE_TYPE_APPLICATION_INFO,
            .pApplicationName = "Battly Vulkan probe",
            .applicationVersion = 1,
            .pEngineName = "Battly",
            .engineVersion = 1,
            .apiVersion = VK_API_VERSION_1_0
    };
    VkInstanceCreateInfo create_info = {
            .sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,
            .pApplicationInfo = &app_info
    };
    VkInstance instance = VK_NULL_HANDLE;
    VkResult result = create_instance(&create_info, NULL, &instance);
    if (result != VK_SUCCESS || instance == VK_NULL_HANDLE) {
        LOGW("Vulkan probe: vkCreateInstance failed with %d", result);
        dlclose(vulkan);
        return JNI_FALSE;
    }

    PFN_vkEnumeratePhysicalDevices enumerate_devices =
            (PFN_vkEnumeratePhysicalDevices) get_instance_proc_addr(
                    instance, "vkEnumeratePhysicalDevices");
    PFN_vkDestroyInstance destroy_instance =
            (PFN_vkDestroyInstance) get_instance_proc_addr(instance, "vkDestroyInstance");
    uint32_t device_count = 0;
    result = enumerate_devices == NULL
            ? VK_ERROR_INITIALIZATION_FAILED
            : enumerate_devices(instance, &device_count, NULL);
    if (destroy_instance != NULL) destroy_instance(instance, NULL);
    dlclose(vulkan);

    if (result != VK_SUCCESS || device_count == 0) {
        LOGW("Vulkan probe: no usable physical device (result=%d, count=%u)",
             result, device_count);
        return JNI_FALSE;
    }
    LOGI("Vulkan probe: %u physical device(s) available", device_count);
    return JNI_TRUE;
}

long shared_awt_surface;

char** convert_to_char_array(JNIEnv *env, jobjectArray jstringArray) {
	int num_rows = (*env)->GetArrayLength(env, jstringArray);
	char **cArray = (char **) malloc(num_rows * sizeof(char*));
	jstring row;
	
	for (int i = 0; i < num_rows; i++) {
		row = (jstring) (*env)->GetObjectArrayElement(env, jstringArray, i);
		cArray[i] = (char*)(*env)->GetStringUTFChars(env, row, 0);
    }
	
    return cArray;
}

jobjectArray convert_from_char_array(JNIEnv *env, char **charArray, int num_rows) {
	jobjectArray resultArr = (*env)->NewObjectArray(env, num_rows, (*env)->FindClass(env, "java/lang/String"), NULL);
	jstring row;
	
	for (int i = 0; i < num_rows; i++) {
		row = (jstring) (*env)->NewStringUTF(env, charArray[i]);
		(*env)->SetObjectArrayElement(env, resultArr, i, row);
    }

	return resultArr;
}

void free_char_array(JNIEnv *env, jobjectArray jstringArray, const char **charArray) {
	int num_rows = (*env)->GetArrayLength(env, jstringArray);
	jstring row;
	
	for (int i = 0; i < num_rows; i++) {
		row = (jstring) (*env)->GetObjectArrayElement(env, jstringArray, i);
		(*env)->ReleaseStringUTFChars(env, row, charArray[i]);
	}
}

jstring convertStringJVM(JNIEnv* srcEnv, JNIEnv* dstEnv, jstring srcStr) {
    if (srcStr == NULL) {
        return NULL;
    }
    
    const char* srcStrC = (*srcEnv)->GetStringUTFChars(srcEnv, srcStr, 0);
    jstring dstStr = (*dstEnv)->NewStringUTF(dstEnv, srcStrC);
	(*srcEnv)->ReleaseStringUTFChars(srcEnv, srcStr, srcStrC);
    return dstStr;
}

JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_setupBridgeSurfaceAWT(JNIEnv *env, jclass clazz, jlong surface) {
	shared_awt_surface = surface;
}

JNIEXPORT jlong JNICALL Java_android_view_Surface_nativeGetBridgeSurfaceAWT(JNIEnv *env, jclass clazz) {
	return (jlong) shared_awt_surface;
}

JNIEXPORT jint JNICALL Java_android_os_OpenJDKNativeRegister_nativeRegisterNatives(JNIEnv *env, jclass clazz, jstring registerSymbol) {
	const char *register_symbol_c = (*env)->GetStringUTFChars(env, registerSymbol, 0);
	void *symbol = dlsym(RTLD_DEFAULT, register_symbol_c);
	if (symbol == NULL) {
		printf("dlsym %s failed: %s\n", register_symbol_c, dlerror());
		return -1;
	}
	
	int (*registerNativesForClass)(JNIEnv*) = symbol;
	int result = registerNativesForClass(env);
	(*env)->ReleaseStringUTFChars(env, registerSymbol, register_symbol_c);
	
	return (jint) result;
}

JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_setLdLibraryPath(JNIEnv *env, jclass clazz, jstring ldLibraryPath) {
	// jclass exception_cls = (*env)->FindClass(env, "java/lang/UnsatisfiedLinkError");
	
	android_update_LD_LIBRARY_PATH_t android_update_LD_LIBRARY_PATH;
	
	void *libdl_handle = dlopen("libdl.so", RTLD_LAZY);
	void *updateLdLibPath = dlsym(libdl_handle, "android_update_LD_LIBRARY_PATH");
	if (updateLdLibPath == NULL) {
		updateLdLibPath = dlsym(libdl_handle, "__loader_android_update_LD_LIBRARY_PATH");
		if (updateLdLibPath == NULL) {
			char *dl_error_c = dlerror();
			LOGE("Error getting symbol android_update_LD_LIBRARY_PATH: %s", dl_error_c);
			// (*env)->ThrowNew(env, exception_cls, dl_error_c);
		}
	}
	
	android_update_LD_LIBRARY_PATH = (android_update_LD_LIBRARY_PATH_t) updateLdLibPath;
	const char* ldLibPathUtf = (*env)->GetStringUTFChars(env, ldLibraryPath, 0);
	android_update_LD_LIBRARY_PATH(ldLibPathUtf);
	(*env)->ReleaseStringUTFChars(env, ldLibraryPath, ldLibPathUtf);
}

JNIEXPORT jboolean JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_dlopen(JNIEnv *env, jclass clazz, jstring name) {
	const char *nameUtf = (*env)->GetStringUTFChars(env, name, 0);
	void* handle = dlopen(nameUtf, RTLD_GLOBAL | RTLD_LAZY);
	if (!handle) {
		LOGE("dlopen %s failed: %s", nameUtf, dlerror());
	} else {
		LOGD("dlopen %s success", nameUtf);
	}
	(*env)->ReleaseStringUTFChars(env, name, nameUtf);
	return handle != NULL;
}

JNIEXPORT jint JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_chdir(JNIEnv *env, jclass clazz, jstring nameStr) {
	const char *name = (*env)->GetStringUTFChars(env, nameStr, NULL);
	int retval = chdir(name);
	(*env)->ReleaseStringUTFChars(env, nameStr, name);
	return retval;
}

JNIEXPORT jint JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_executeBinary(JNIEnv *env, jclass clazz, jobjectArray cmdArgs) {
	jclass exception_cls = (*env)->FindClass(env, "java/lang/UnsatisfiedLinkError");
	jstring execFile = (*env)->GetObjectArrayElement(env, cmdArgs, 0);
	
	char *exec_file_c = (char*) (*env)->GetStringUTFChars(env, execFile, 0);
	void *exec_binary_handle = dlopen(exec_file_c, RTLD_LAZY);
	
	// (*env)->ReleaseStringUTFChars(env, ldLibraryPath, ld_library_path_c);
	(*env)->ReleaseStringUTFChars(env, execFile, exec_file_c);
	
	char *exec_error_c = dlerror();
	if (exec_error_c != NULL) {
		LOGE("Error: %s", exec_error_c);
		(*env)->ThrowNew(env, exception_cls, exec_error_c);
		return -1;
	}
	
	Main_Function_t Main_Function;
	Main_Function = (Main_Function_t) dlsym(exec_binary_handle, "main");
	
	exec_error_c = dlerror();
	if (exec_error_c != NULL) {
		LOGE("Error: %s", exec_error_c);
		(*env)->ThrowNew(env, exception_cls, exec_error_c);
		return -1;
	}
	
	int cmd_argv = (*env)->GetArrayLength(env, cmdArgs);
	char **cmd_args_c = convert_to_char_array(env, cmdArgs);
	int result = Main_Function(cmd_argv, cmd_args_c);
	free_char_array(env, cmdArgs, cmd_args_c);
	return result;
}

JNIEnv* get_attached_env(JavaVM* jvm) {
    if (jvm == NULL) {
        printf("get_attached_env failed: JavaVM is null\n");
        return NULL;
    }
    JNIEnv *jvm_env = NULL;
    jint env_result = (*jvm)->GetEnv(jvm, (void**)&jvm_env, JNI_VERSION_1_4);
    if(env_result == JNI_EDETACHED) {
        env_result = (*jvm)->AttachCurrentThread(jvm, &jvm_env, NULL);
    }
    if(env_result != JNI_OK) {
        printf("get_attached_env failed: %i\n", env_result);
        return NULL;
    }
    return jvm_env;
}

// METHOD 2
/*
JNIEXPORT jint JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_executeForkedBinary(JNIEnv *env, jclass clazz, jobjectArray cmdArgs) {
	int x, status;
	x = fork();
	if (x > 0) {
		wait(&status);
	} else {
		execvpe();
	}
	return status;
}
*/

