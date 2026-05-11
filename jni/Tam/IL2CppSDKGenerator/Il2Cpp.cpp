#include <android/log.h>
#include <map>
#include <jni.h>
#include <unistd.h>
#include "Il2cpp.h"

#define g_LogTag "IL2CPP-LOGGER"

typedef unsigned short UTF16;
typedef wchar_t UTF32;
typedef char UTF8;

namespace {
    const void *(*il2cpp_assembly_get_image)(const void *assembly);
    void *(*il2cpp_domain_get)();
    void **(*il2cpp_domain_get_assemblies)(const void *domain, size_t *size);
    const char *(*il2cpp_image_get_name)(void *image);
    void *(*il2cpp_class_from_name)(const void *image, const char *namespaze, const char *name);
    void *(*il2cpp_class_get_field_from_name)(void *klass, const char *name);
    void *(*il2cpp_class_get_method_from_name)(void *klass, const char *name, int argsCount);
    size_t (*il2cpp_field_get_offset)(void *field);
    void (*il2cpp_field_static_get_value)(void *field, void *value);
    uint16_t *(*il2cpp_string_chars)(void *str);
    Il2CppString *(*il2cpp_string_new)(const char *str);
    Il2CppString *(*il2cpp_string_new_utf16)(const wchar_t *str, int32_t length);
    void *(*il2cpp_object_new)(void *klass);
}

int is_surrogate(UTF16 uc) { return (uc - 0xd800u) < 2048u; }
int is_high_surrogate(UTF16 uc) { return (uc & 0xfffffc00) == 0xd800; }
int is_low_surrogate(UTF16 uc) { return (uc & 0xfffffc00) == 0xdc00; }

UTF32 surrogate_to_utf32(UTF16 high, UTF16 low) {
    return (high << 10) + low - 0x35fdc00;
}

const char* utf16_to_utf8(const UTF16* source, size_t len) {
    std::u16string s(source, source + len);
    std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t> convert;
    return convert.to_bytes(s).c_str();
}

const wchar_t* utf16_to_utf32(const UTF16* source, size_t len) {
    auto output = new UTF32[len + 1];
    for (int i = 0; i < len; i++) {
        const UTF16 uc = source[i];
        if (!is_surrogate(uc)) {
            output[i] = uc;
        } else {
            if (is_high_surrogate(uc) && is_low_surrogate(source[i]))
                output[i] = surrogate_to_utf32(uc, source[i]);
            else
                output[i] = L'?';
        }
    }
    output[len] = L'\0';
    return output;
}

const char* Il2CppString::CString() {
    return utf16_to_utf8(&this->start_char, this->length);
}

const wchar_t* Il2CppString::WCString() {
    return utf16_to_utf32(&this->start_char, this->length);
}

Il2CppString* Il2CppString::Create(const char *s) {
    return il2cpp_string_new(s);
}

Il2CppString* Il2CppString::Create(const wchar_t *s, int len) {
    return il2cpp_string_new_utf16(s, len);
}

void* IL2Cpp::Il2CppGetImageByName(const char *image) {
    size_t size;
    void **assemblies = il2cpp_domain_get_assemblies(il2cpp_domain_get(), &size);
    for (int i = 0; i < size; ++i) {
        void *img = (void *)il2cpp_assembly_get_image(assemblies[i]);
        const char *img_name = il2cpp_image_get_name(img);
        if (strcmp(img_name, image) == 0) return img;
    }
    return 0;
}

void* IL2Cpp::Il2CppGetClassType(const char *image, const char *namespaze, const char *clazz) {
    static std::map<std::string, void*> cache;
    std::string s = std::string(image) + namespaze + clazz;
    if (cache.count(s) > 0) return cache[s];
    void *img = IL2Cpp::Il2CppGetImageByName(image);
    if (!img) {
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "Can't find image %s!", image);
        return 0;
    }
    void *klass = il2cpp_class_from_name(img, namespaze, clazz);
    if (!klass) {
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "Can't find class %s!", clazz);
        return 0;
    }
    cache[s] = klass;
    return klass;
}

void IL2Cpp::Il2CppGetStaticFieldValue(const char *image, const char *namespaze, const char *clazz, const char *name, void *output) {
    void *img = IL2Cpp::Il2CppGetImageByName(image);
    if (!img) {
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "Can't find image %s!", image);
        return;
    }
    void *klass = IL2Cpp::Il2CppGetClassType(image, namespaze, clazz);
    if (!klass) {
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "Can't find class %s for field %s!", clazz, name);
        return;
    }
    void *field = il2cpp_class_get_field_from_name(klass, name);
    if (!field) {
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "Can't find field %s in class %s!", name, clazz);
        return;
    }
    il2cpp_field_static_get_value(field, output);
}

void* IL2Cpp::Il2CppGetMethodOffset(const char *image, const char *namespaze, const char *clazz, const char *name, int argsCount) {
    void *img = IL2Cpp::Il2CppGetImageByName(image);
    if (!img) {
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "Can't find image %s!", image);
        return 0;
    }
    void *klass = IL2Cpp::Il2CppGetClassType(image, namespaze, clazz);
    if (!klass) {
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "Can't find class %s for method %s!", clazz, name);
        return 0;
    }
    void **method = (void**)il2cpp_class_get_method_from_name(klass, name, argsCount);
    if (!method) {
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "Can't find method %s in class %s!", name, clazz);
        return 0;
    }
    __android_log_print(ANDROID_LOG_DEBUG, g_LogTag, "%s - [%s] %s::%s: %p", image, namespaze, clazz, name, *method);
    return *method;
}

void* IL2Cpp::Il2CppSwapMethodPointer(const char* image, const char* namespaze, const char* clazz, const char* name, int argsCount, void* new_func) {
    void *img = IL2Cpp::Il2CppGetImageByName(image);
    if (!img) return nullptr;

    void *klass = IL2Cpp::Il2CppGetClassType(image, namespaze, clazz);
    if (!klass) return nullptr;

    MethodInfo *method = (MethodInfo*)il2cpp_class_get_method_from_name(klass, name, argsCount);
    if (!method) return nullptr;

    void* orig_func = (void*)method->methodPointer;
    method->methodPointer = (Il2CppMethodPointer)new_func;
    return orig_func;
}

size_t IL2Cpp::Il2CppGetFieldOffset(const char *image, const char *namespaze, const char *clazz, const char *name) {
    void *img = IL2Cpp::Il2CppGetImageByName(image);
    if (!img) {
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "Can't find image %s!", image);
        return -1;
    }
    void *klass = IL2Cpp::Il2CppGetClassType(image, namespaze, clazz);
    if (!klass) {
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "Can't find class %s for field %s!", clazz, name);
        return -1;
    }
    void *field = il2cpp_class_get_field_from_name(klass, name);
    if (!field) {
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "Can't find field %s in class %s!", clazz, name);
        return -1;
    }
    auto result = il2cpp_field_get_offset(field);
    __android_log_print(ANDROID_LOG_DEBUG, g_LogTag, "%s - [%s] %s::%s: %p", image, namespaze, clazz, name, (void *)result);
    return result;
}

size_t IL2Cpp::Il2CppGetStaticFieldOffset(const char *image, const char *namespaze, const char *clazz, const char *name){
    void *img = IL2Cpp::Il2CppGetImageByName(image);
    if(!img) {
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "Can't find image %s!", image);
        return -1;
    }
    void *klass = IL2Cpp::Il2CppGetClassType(image, namespaze, clazz);
    if(!klass) {
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "Can't find class %s for field %s!", clazz, name);
        return -1;
    }
    FieldInfo *field = (FieldInfo*)il2cpp_class_get_field_from_name(klass, name);
    if(!field) {
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "Can't find field %s in class %s!", clazz, name);
        return -1;
    }
    return (unsigned long)((uint64_t)field->parent->static_fields + field->offset);
}

void IL2Cpp::Il2CppAttach(const char *name) {
    void *handle = dlopen_ex(name, 0);
    while (!handle) {
        handle = dlopen_ex(name, 0);
        sleep(1);
    }
    il2cpp_assembly_get_image = (const void *(*)(const void *)) dlsym_ex(handle, "il2cpp_assembly_get_image");
    il2cpp_domain_get = (void *(*)()) dlsym_ex(handle, "il2cpp_domain_get");
    il2cpp_domain_get_assemblies = (void **(*)(const void*, size_t*)) dlsym_ex(handle, "il2cpp_domain_get_assemblies");
    il2cpp_image_get_name = (const char *(*)(void *)) dlsym_ex(handle, "il2cpp_image_get_name");
    il2cpp_class_from_name = (void* (*)(const void*, const char*, const char*)) dlsym_ex(handle, "il2cpp_class_from_name");
    il2cpp_class_get_field_from_name = (void* (*)(void*, const char*)) dlsym_ex(handle, "il2cpp_class_get_field_from_name");
    il2cpp_class_get_method_from_name = (void* (*)(void *, const char*, int)) dlsym_ex(handle, "il2cpp_class_get_method_from_name");
    il2cpp_field_get_offset = (size_t (*)(void *)) dlsym_ex(handle, "il2cpp_field_get_offset");
    il2cpp_field_static_get_value = (void (*)(void*, void*)) dlsym_ex(handle, "il2cpp_field_static_get_value");
    il2cpp_string_new = (Il2CppString *(*)(const char*)) dlsym_ex(handle, "il2cpp_string_new");
    il2cpp_string_new_utf16 = (Il2CppString *(*)(const wchar_t*, int32_t)) dlsym_ex(handle, "il2cpp_string_new_utf16");
    il2cpp_object_new = (void *(*)(void*)) dlsym_ex(handle, "il2cpp_object_new");
    dlclose_ex(handle);
}