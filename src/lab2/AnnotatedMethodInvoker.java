package lab2;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Класс-вызыватель. Через рефлексию находит все методы {@link AnnotatedClass},
 * помеченные аннотацией {@link Repeat}, повторяет вызов только для защищённых
 * и приватных методов. Код не зависит от количества и типов параметров методов:
 * аргументы подставляются автоматически по итпу параметра.
 */
public final class AnnotatedMethodInvoker {

    private AnnotatedMethodInvoker() {
    }

    /**
     * Вызывает все аннотированные protected/private методы класса {@code type}
     * столько раз, сколько указано в параметре аннотации.
     */
    public static void invokeAnnotatedProtectedAndPrivate(Class<?> type, Object instance) throws ReflectiveOperationException {
        for (Method method : type.getDeclaredMethods()) {
            Repeat repeat = method.getAnnotation(Repeat.class);
            if (repeat == null) {
                continue; // метод не аннотирован
            }

            int modifiers = method.getModifiers();
            boolean isProtected = Modifier.isProtected(modifiers);
            boolean isPrivate = Modifier.isPrivate(modifiers);
            if (!isProtected && !isPrivate) {
                continue; // вызываем только protected и private
            }

            int times = repeat.value();
            Object[] args = prepareArguments(method);

            method.setAccessible(true);
            System.out.printf("== Вызов %s %s(%s) %d раз(а)...%n",
                    isPrivate ? "private" : "protected",
                    method.getName(),
                    signature(method),
                    times);

            for (int i = 0; i < times; i++) {
                method.invoke(instance, args);
            }
            System.out.println();
        }
    }

    /** Формирует аргументы под вызываемый метод по типам параметров. */
    private static Object[] prepareArguments(Method method) throws ReflectiveOperationException {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] args = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            args[i] = defaultObject(parameterTypes[i]);
        }
        return args;
    }

    /** Возвращает строковое представление сигнатуры (имена параметров). */
    private static String signature(Method method) {
        StringBuilder sb = new StringBuilder();
        Class<?>[] params = method.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(params[i].getSimpleName());
        }
        return sb.toString();
    }

    /**
     * Возвращает значение по умолчанию для параметра заданного типа.
     * Гарантированно не возвращает {@code null} (требование задачи).
     */
    public static Object defaultObject(Class<?> type) throws ReflectiveOperationException {
        if (type == void.class) {
            return null;
        }
        if (type.isPrimitive()) {
            return primitiveDefault(type);
        }
        if (type == String.class) {
            return ""; // пустая строка вместо null
        }
        if (type == Character.class) {
            return '\0';
        }
        // Любой другой ссылочный тип -> создаём не-null экземпляр.
        return newInstanceOrUnsafe(type);
    }

    private static Object primitiveDefault(Class<?> type) {
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == float.class) return 0.0F;
        if (type == double.class) return 0.0D;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        return 0;
    }

    /**
     * Даёт не-null экземпляр ссылочного типа: сначала через public/недоступный
     * конструктор без параметров, затем через {@code Unsafe.allocateInstance}
     * (создаёт объект без вызова конструктора) — чтобы гарантировать, что в
     * метод не передаётся {@code null}.
     */
    private static Object newInstanceOrUnsafe(Class<?> type) throws ReflectiveOperationException {
        try {
            Constructor<?> ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (NoSuchMethodException e) {
            try {
                return allocateUnsafe(type);
            } catch (ReflectiveOperationException ex) {
                throw new RuntimeException(
                        "Не удалось создать не-null экземпляр типа " + type.getName(), ex);
            }
        }
    }

    private static Object allocateUnsafe(Class<?> type) throws ReflectiveOperationException {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        java.lang.reflect.Field field = unsafeClass.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = field.get(null);
        Method allocate = unsafeClass.getMethod("allocateInstance", Class.class);
        return allocate.invoke(unsafe, type);
    }
}