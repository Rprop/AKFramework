#pragma once
#include <jni.h>

#if !defined(DISALLOW_COPY_AND_ASSIGN)
// DISALLOW_COPY_AND_ASSIGN disallows the copy and operator= functions.
// It goes in the private: declarations in a class.
#define DISALLOW_COPY_AND_ASSIGN(TypeName) \
  TypeName(const TypeName&) = delete;      \
  void operator=(const TypeName&) = delete
#endif // !defined(DISALLOW_COPY_AND_ASSIGN)

class ScopedLocalFrame
{
public:
    // Number of local references in the indirect reference table. The value is arbitrary but
    // low enough that it forces sanity checks.
    static constexpr size_t kLocalsInitial = 512u;

public:
    ScopedLocalFrame(JNIEnv *env, jint capacity = kLocalsInitial / 2u) : mEnv(env) {
        env->PushLocalFrame(capacity);
    }

    ~ScopedLocalFrame() {
        this->Pop(NULL);
    }

    jobject Pop(jobject java_survivor) {
        if (this->mEnv != NULL) {
            java_survivor = this->mEnv->PopLocalFrame(java_survivor);
            this->mEnv    = NULL;
            return java_survivor;
        } //if

        return NULL;
    }

private:
    JNIEnv *mEnv;
    DISALLOW_COPY_AND_ASSIGN(ScopedLocalFrame);
};

template<typename T = jobject> 
class ScopedLocalRef
{
public:
    ScopedLocalRef(JNIEnv *env, T localRef) : mEnv(env), mLocalRef(localRef) {
        this->ensureNoThrow();
    }

    ScopedLocalRef(JNIEnv *env, const void *localRef) : ScopedLocalRef(env, static_cast<T>(const_cast<void *>(localRef))) {}

    ~ScopedLocalRef() {
        this->reset();
    }

    void ensureNoThrow() {
        if (this->mEnv->ExceptionCheck()) {       
            this->mEnv->ExceptionDescribe(); 
            this->mEnv->ExceptionClear();
            this->mLocalRef = NULL;
        } //if
    }

    void reset(T ptr = nullptr) {
        if (ptr != this->mLocalRef) {
            if (this->mLocalRef != nullptr) {
                this->mEnv->DeleteLocalRef(this->mLocalRef);
            } //if
            this->mLocalRef = ptr;
            this->ensureNoThrow();
        } //if
    }

    T release() __attribute__((warn_unused_result)) {
        T localRef = this->mLocalRef;
        this->mLocalRef = NULL;
        return localRef;
    }

    T get() const {
        return this->mLocalRef;
    }

    operator T() const {
        return this->mLocalRef;
    }

private:
    JNIEnv *const mEnv;
    T mLocalRef;
    DISALLOW_COPY_AND_ASSIGN(ScopedLocalRef);
};

class ScopedUtfChars
{
public:
    ScopedUtfChars(JNIEnv *env, jstring js) : mEnv(env), mString(js) {
        if (js == NULL) {
            this->mUtfChars = NULL;
        } else {
            this->mUtfChars = env->GetStringUTFChars(this->mString, NULL);
        } //if
    }

    ScopedUtfChars(ScopedUtfChars &&rhs) : mEnv(rhs.mEnv), mString(rhs.mString), mUtfChars(rhs.mUtfChars) {
        rhs.mEnv      = NULL;
        rhs.mString   = NULL;
        rhs.mUtfChars = NULL;
    }

    ~ScopedUtfChars() {
        if (this->mUtfChars != NULL) {
            this->mEnv->ReleaseStringUTFChars(this->mString, this->mUtfChars);
        } //if
    }

    ScopedUtfChars &operator = (ScopedUtfChars&& rhs) {
        if (this != &rhs) {
            // Delete the currently owned UTF chars.
            this->~ScopedUtfChars();

            // Move the rhs ScopedUtfChars and zero it out.
            this->mEnv      = rhs.mEnv;
            this->mString   = rhs.mString;
            this->mUtfChars = rhs.mUtfChars;
            rhs.mEnv      = NULL;
            rhs.mString   = NULL;
            rhs.mUtfChars = NULL;
        } //if
        return *this;
    }

    const char *c_str() const {
        return this->mUtfChars;
    }

    size_t c_size() const {
        return strlen(this->mUtfChars);
    }

    jsize size() const {
        return this->mEnv->GetStringUTFLength(this->mString);
    }

    operator const char *() const {
        return this->mUtfChars;
    }

    const char &operator [] (const size_t n) const {
        return this->mUtfChars[n];
    }

private:
    JNIEnv     *mEnv;
    jstring     mString;
    const char *mUtfChars;
    DISALLOW_COPY_AND_ASSIGN(ScopedUtfChars);
};