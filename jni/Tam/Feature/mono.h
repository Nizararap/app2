#pragma once

template <typename T>
struct monoArray {
    void* klass;
    void* monitor;
    void* bounds;
    int   max_length;
    void* vector[1];
    int getLength() { return max_length; }
    T getPointer() { return (T)vector; }
};

template <typename T>
struct monoList {
    void *unk0;
    void *unk1;
    monoArray<T> *items;
    int size;
    int version;

    T getItems() { return items->getPointer(); }
    int getSize() { return size; }
    int getVersion() { return version; }
};