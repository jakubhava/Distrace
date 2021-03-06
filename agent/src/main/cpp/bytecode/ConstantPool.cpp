//
// Created by Jakub Háva on 18/10/2016.
//

#include "ConstantPool.h"
#include "Constant.h"
#include "JavaConst.h"
#include "ConstantClass.h"
#include "ConstantUtf8.h"
#include "ConstantString.h"

using namespace Distrace;

ConstantPool::ConstantPool(ByteReader &reader) : reader(reader) {
    constant_pool_count = reader.readShort();
    constant_pool = new Constant*[constant_pool_count];
    byte tag;
    /* constant_pool[0] is unused by the compiler and may be used freely
     * by the implementation.
     */
    for (int i = 1; i < constant_pool_count; i++) {
        constant_pool[i] = Constant::readConstant(reader);

        /* Quote from the JVM specification:
         * "All eight byte constants take up two spots in the constant pool.
         * If this is the n'th byte in the constant pool, then the next item
         * will be numbered n+2"
         *
         * Thus we have to increment the index counter.
         */
        tag = constant_pool[i]->getTag();

        if ((tag == JavaConst::CONSTANT_Double) || (tag == JavaConst::CONSTANT_Long)) {
            i++;
        }
    }
}

Constant* ConstantPool::getConstant(int index, byte tag) {

    Constant *c = getConstant(index);
    if (c == NULL) {
        throw std::runtime_error("Constant pool at index " + std::to_string(index) + " is null.");
    }
    if (c->getTag() != tag) {
        throw std::runtime_error("Expected class `" + JavaConst::getConstantName(tag) + "'");
    }
    return c;
}

Constant* ConstantPool::getConstant(int index) {
    if (index >= constant_pool_count || index < 0) {
        throw std::runtime_error("Invalid constant pool reference: " + std::to_string(index)+
                                         ". Constant pool size is: " + std::to_string(constant_pool_count));
    }
    return constant_pool[index];
}

std::string ConstantPool::getConstantString(int index, byte tag) {



    Constant* c = getConstant(index, tag);

    /* This switch() is not that elegant, since the two classes have the
     * same contents, they just differ in the name of the index
     * field variable.
     * But we want to stick to the JVM naming conventions closely though
     * we could have solved these more elegantly by using the same
     * variable name or by subclassing.
     */
    int    i;
    switch(tag) {
        case JavaConst::CONSTANT_Class:
            i = ((ConstantClass*)c)->getNameIndex();
            break;
        case JavaConst::CONSTANT_String:
            i = ((ConstantString*)c)->getStringIndex();
            break;
        default:
            throw std::runtime_error("getConstantString called with illegal tag " + std::to_string(tag));
    }

    // Finally get the string from the constant pool
    c = getConstant(i, JavaConst::CONSTANT_Utf8);
    return ((ConstantUtf8*)c)->getBytes();
}

