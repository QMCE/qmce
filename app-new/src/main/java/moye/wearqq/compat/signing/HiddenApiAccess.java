package moye.wearqq.compat.signing;

import android.util.Log;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.HashSet;

final class HiddenApiAccess {
    private static final String TAG = "HiddenApiAccess";
    private static final HashSet<String> EXEMPTIONS = new HashSet<>();
    private static final UnsafeBridge UNSAFE;
    private static final long EXECUTABLE_ART_METHOD_OFFSET;
    private static final long CLASS_METHODS_OFFSET;
    private static final long ART_METHOD_SIZE;
    private static final long ART_METHOD_BIAS;

    static {
        try {
            UnsafeBridge unsafe = UnsafeBridge.create();
            UNSAFE = unsafe;
            EXECUTABLE_ART_METHOD_OFFSET = unsafe.objectFieldOffset(
                    ExecutableMirror.class.getDeclaredField("artMethod")
            );
            long methodHandleArtOffset = unsafe.objectFieldOffset(
                    MethodHandleMirror.class.getDeclaredField("artFieldOrMethod")
            );
            CLASS_METHODS_OFFSET = unsafe.objectFieldOffset(
                    ClassMirror.class.getDeclaredField("methods")
            );
            long instanceFieldsOffset = unsafe.objectFieldOffset(
                    ClassMirror.class.getDeclaredField("iFields")
            );
            Method firstMethod = DummyMembers.class.getDeclaredMethod("first");
            Method secondMethod = DummyMembers.class.getDeclaredMethod("second");
            firstMethod.setAccessible(true);
            secondMethod.setAccessible(true);
            MethodHandle firstHandle = MethodHandles.lookup().unreflect(firstMethod);
            MethodHandle secondHandle = MethodHandles.lookup().unreflect(secondMethod);
            long firstArtMethod = unsafe.getLong(firstHandle, methodHandleArtOffset);
            long secondArtMethod = unsafe.getLong(secondHandle, methodHandleArtOffset);
            long dummyMethods = unsafe.getLong(DummyMembers.class, CLASS_METHODS_OFFSET);
            ART_METHOD_SIZE = secondArtMethod - firstArtMethod;
            ART_METHOD_BIAS = firstArtMethod - dummyMethods - ART_METHOD_SIZE;

            Field firstField = DummyMembers.class.getDeclaredField("firstField");
            Field secondField = DummyMembers.class.getDeclaredField("secondField");
            firstField.setAccessible(true);
            secondField.setAccessible(true);
            MethodHandles.lookup().unreflectGetter(firstField);
            MethodHandles.lookup().unreflectGetter(secondField);
            unsafe.getLong(DummyMembers.class, instanceFieldsOffset);
        } catch (ReflectiveOperationException error) {
            Log.e(TAG, "initialize failed", error);
            throw new ExceptionInInitializerError(error);
        }
    }

    private HiddenApiAccess() {
    }

    static boolean addHiddenApiExemptions(String... prefixes) {
        try {
            Class<?> vmRuntimeClass = vmRuntimeClass();
            Object runtime = invokeHiddenMethod(vmRuntimeClass, null, "getRuntime");
            invokeHiddenMethod(vmRuntimeClass, runtime, "setHiddenApiExemptions", (Object) prefixes);
            EXEMPTIONS.addAll(java.util.Arrays.asList(prefixes));
            return true;
        } catch (Throwable error) {
            Log.w(TAG, "setHiddenApiExemptions failed", error);
            return false;
        }
    }

    private static Class<?> vmRuntimeClass() throws ReflectiveOperationException {
        Method forName = Class.class.getDeclaredMethod("forName", String.class);
        return (Class<?>) forName.invoke(null, "dalvik.system.VMRuntime");
    }

    private static Object invokeHiddenMethod(
            Class<?> owner,
            Object instance,
            String methodName,
            Object... args
    ) throws ReflectiveOperationException {
        if (instance != null && !owner.isInstance(instance)) {
            throw new IllegalArgumentException("this object is not an instance of the given class");
        }

        Method bridge = HiddenMethodInvoker.class.getDeclaredMethod("invoke", Object[].class);
        bridge.setAccessible(true);
        long methods = UNSAFE.getLong(owner, CLASS_METHODS_OFFSET);
        if (methods == 0L) {
            throw new NoSuchMethodException("Cannot find matching method");
        }

        int methodCount = UNSAFE.getInt(methods);
        for (int index = 0; index < methodCount; index++) {
            long methodPointer = methods + ART_METHOD_BIAS + (long) index * ART_METHOD_SIZE;
            UNSAFE.putLong(bridge, EXECUTABLE_ART_METHOD_OFFSET, methodPointer);
            if (methodName.equals(bridge.getName()) && matches(bridge.getParameterTypes(), args)) {
                return bridge.invoke(instance, args);
            }
        }
        throw new NoSuchMethodException("Cannot find matching method");
    }

    private static boolean matches(Class<?>[] parameterTypes, Object[] args) {
        if (parameterTypes.length != args.length) {
            return false;
        }
        for (int index = 0; index < parameterTypes.length; index++) {
            Class<?> parameterType = parameterTypes[index];
            Object value = args[index];
            if (parameterType.isPrimitive()) {
                if (!matchesPrimitive(parameterType, value)) {
                    return false;
                }
            } else if (value != null && !parameterType.isInstance(value)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesPrimitive(Class<?> parameterType, Object value) {
        if (parameterType == int.class) {
            return value instanceof Integer;
        }
        if (parameterType == byte.class) {
            return value instanceof Byte;
        }
        if (parameterType == char.class) {
            return value instanceof Character;
        }
        if (parameterType == boolean.class) {
            return value instanceof Boolean;
        }
        if (parameterType == double.class) {
            return value instanceof Double;
        }
        if (parameterType == float.class) {
            return value instanceof Float;
        }
        if (parameterType == long.class) {
            return value instanceof Long;
        }
        if (parameterType == short.class) {
            return value instanceof Short;
        }
        return false;
    }

    private static class AccessibleObjectMirror {
        @SuppressWarnings("unused")
        private boolean override;
    }

    private static class ClassMirror {
        @SuppressWarnings("unused")
        private transient int accessFlags;
        @SuppressWarnings("unused")
        private transient int classFlags;
        @SuppressWarnings("unused")
        private transient ClassLoader classLoader;
        @SuppressWarnings("unused")
        private transient int classSize;
        @SuppressWarnings("unused")
        private transient int clinitThreadId;
        @SuppressWarnings("unused")
        private transient Class<?> componentType;
        @SuppressWarnings("unused")
        private transient short copiedMethodsOffset;
        @SuppressWarnings("unused")
        private transient Object dexCache;
        @SuppressWarnings("unused")
        private transient int dexClassDefIndex;
        @SuppressWarnings("unused")
        private volatile transient int dexTypeIndex;
        @SuppressWarnings("unused")
        private transient Object extData;
        @SuppressWarnings("unused")
        private transient long iFields;
        @SuppressWarnings("unused")
        private transient Object[] ifTable;
        @SuppressWarnings("unused")
        private transient long methods;
        @SuppressWarnings("unused")
        private transient String name;
        @SuppressWarnings("unused")
        private transient int numReferenceInstanceFields;
        @SuppressWarnings("unused")
        private transient int numReferenceStaticFields;
        @SuppressWarnings("unused")
        private transient int objectSize;
        @SuppressWarnings("unused")
        private transient int objectSizeAllocFastPath;
        @SuppressWarnings("unused")
        private transient int primitiveType;
        @SuppressWarnings("unused")
        private transient int referenceInstanceOffsets;
        @SuppressWarnings("unused")
        private transient long sFields;
        @SuppressWarnings("unused")
        private transient int status;
        @SuppressWarnings("unused")
        private transient Class<?> superClass;
        @SuppressWarnings("unused")
        private transient short virtualMethodsOffset;
        @SuppressWarnings("unused")
        private transient Object vtable;
    }

    private static class ExecutableMirror extends AccessibleObjectMirror {
        @SuppressWarnings("unused")
        private int accessFlags;
        @SuppressWarnings("unused")
        private long artMethod;
        @SuppressWarnings("unused")
        private ClassMirror declaringClass;
        @SuppressWarnings("unused")
        private ClassMirror declaringClassOfOverriddenMethod;
        @SuppressWarnings("unused")
        private Object[] parameters;
    }

    private static class MethodHandleMirror {
        @SuppressWarnings("unused")
        protected long artFieldOrMethod;
        @SuppressWarnings("unused")
        private MethodHandleMirror cachedSpreadInvoker;
        @SuppressWarnings("unused")
        protected int handleKind;
        @SuppressWarnings("unused")
        private MethodType nominalType;
        @SuppressWarnings("unused")
        private MethodType type;
    }

    private static class HandleInfoMirror extends MethodHandleMirror {
        @SuppressWarnings("unused")
        private MethodHandleInfo info;
    }

    private static class MethodHandleInfoMirror {
        @SuppressWarnings("unused")
        private MethodHandleMirror handle;
        @SuppressWarnings("unused")
        private Member member;
    }

    private static class HiddenMethodInvoker {
        @SuppressWarnings("unused")
        private static Object invoke(Object... args) {
            throw new IllegalStateException("Failed to invoke the method");
        }
    }

    private static class DummyMembers {
        @SuppressWarnings("unused")
        private static int staticFirst;
        @SuppressWarnings("unused")
        private static int staticSecond;
        @SuppressWarnings("unused")
        private int firstField;
        @SuppressWarnings("unused")
        private int secondField;

        @SuppressWarnings("unused")
        private static void first() {
        }

        @SuppressWarnings("unused")
        private static void second() {
        }
    }

    private static final class UnsafeBridge {
        private final Object unsafe;
        private final Method objectFieldOffset;
        private final Method getLongObject;
        private final Method getLongAddress;
        private final Method getIntAddress;
        private final Method putLongObject;

        private UnsafeBridge(
                Object unsafe,
                Method objectFieldOffset,
                Method getLongObject,
                Method getLongAddress,
                Method getIntAddress,
                Method putLongObject
        ) {
            this.unsafe = unsafe;
            this.objectFieldOffset = objectFieldOffset;
            this.getLongObject = getLongObject;
            this.getLongAddress = getLongAddress;
            this.getIntAddress = getIntAddress;
            this.putLongObject = putLongObject;
        }

        static UnsafeBridge create() throws ReflectiveOperationException {
            Class<?> unsafeClass = unsafeClass();
            Object unsafe = findUnsafeInstance(unsafeClass);
            return new UnsafeBridge(
                    unsafe,
                    unsafeClass.getMethod("objectFieldOffset", Field.class),
                    unsafeClass.getMethod("getLong", Object.class, long.class),
                    unsafeClass.getMethod("getLong", long.class),
                    unsafeClass.getMethod("getInt", long.class),
                    unsafeClass.getMethod("putLong", Object.class, long.class, long.class)
            );
        }

        long objectFieldOffset(Field field) throws ReflectiveOperationException {
            return (Long) objectFieldOffset.invoke(unsafe, field);
        }

        long getLong(Object target, long offset) throws ReflectiveOperationException {
            return (Long) getLongObject.invoke(unsafe, target, offset);
        }

        long getLong(long address) throws ReflectiveOperationException {
            return (Long) getLongAddress.invoke(unsafe, address);
        }

        int getInt(long address) throws ReflectiveOperationException {
            return (Integer) getIntAddress.invoke(unsafe, address);
        }

        void putLong(Object target, long offset, long value) throws ReflectiveOperationException {
            putLongObject.invoke(unsafe, target, offset, value);
        }

        private static Class<?> unsafeClass() throws ReflectiveOperationException {
            Method forName = Class.class.getDeclaredMethod("forName", String.class);
            return (Class<?>) forName.invoke(null, "sun.misc.Unsafe");
        }

        private static Object findUnsafeInstance(Class<?> unsafeClass) throws ReflectiveOperationException {
            try {
                Method getUnsafe = unsafeClass.getDeclaredMethod("getUnsafe");
                return getUnsafe.invoke(null);
            } catch (Throwable ignored) {
                Field field = unsafeClass.getDeclaredField("theUnsafe");
                field.setAccessible(true);
                return field.get(null);
            }
        }
    }
}
