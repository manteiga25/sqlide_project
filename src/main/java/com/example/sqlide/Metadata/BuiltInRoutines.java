package com.example.sqlide.Metadata;

import com.example.sqlide.drivers.model.SQLTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * Funções que vêm com cada motor.
 *
 * <p>Ao contrário das rotinas criadas por SQL, estas não estão em catálogo nenhum no SQLite
 * nem no MySQL — só o PostgreSQL as expõe no {@code pg_proc}. Ficam aqui descritas para o
 * catálogo poder mostrar assinatura e descrição de todas por igual.</p>
 */
public final class BuiltInRoutines {

    private BuiltInRoutines() {
    }

    public static ArrayList<RoutineMetadata> forDialect(SQLTypes dialect) {
        ArrayList<RoutineMetadata> routines = new ArrayList<>(common());
        switch (dialect) {
            case SQLITE -> routines.addAll(sqlite());
            case MYSQL -> routines.addAll(mysql());
            case POSTGRESQL -> routines.addAll(postgresql());
            case MS_ACCESS -> routines.addAll(access());
        }
        return routines;
    }

    private static RoutineMetadata fn(String name, String category, String parameters,
                                      String returnType, String comment) {
        return new RoutineMetadata(name, RoutineMetadata.Kind.FUNCTION, category,
                parameters, returnType, comment, true);
    }

    private static RoutineMetadata agg(String name, String parameters, String returnType, String comment) {
        return new RoutineMetadata(name, RoutineMetadata.Kind.AGGREGATE, "Aggregate",
                parameters, returnType, comment, true);
    }

    /** Presentes em todos os motores suportados. */
    private static List<RoutineMetadata> common() {
        return List.of(
                agg("COUNT", "(expression)", "INTEGER", "Number of rows where the expression is not null. COUNT(*) counts every row."),
                agg("SUM", "(expression)", "NUMERIC", "Total of the values, ignoring nulls."),
                agg("AVG", "(expression)", "REAL", "Arithmetic mean of the values, ignoring nulls."),
                agg("MIN", "(expression)", "any", "Smallest value."),
                agg("MAX", "(expression)", "any", "Largest value."),

                fn("ABS", "Math", "(number)", "NUMERIC", "Absolute value."),
                fn("ROUND", "Math", "(number, decimals)", "NUMERIC", "Rounds to the given number of decimal places."),
                fn("COALESCE", "Conditional", "(value1, value2, ...)", "any", "First argument that is not null."),
                fn("NULLIF", "Conditional", "(value1, value2)", "any", "Null when both arguments are equal, otherwise the first."),
                fn("CAST", "Conversion", "(expression AS type)", "any", "Converts a value to another type."),

                fn("LENGTH", "String", "(text)", "INTEGER", "Number of characters in the text."),
                fn("LOWER", "String", "(text)", "TEXT", "Text in lower case."),
                fn("UPPER", "String", "(text)", "TEXT", "Text in upper case."),
                fn("TRIM", "String", "(text)", "TEXT", "Removes leading and trailing spaces."),
                fn("REPLACE", "String", "(text, search, replacement)", "TEXT", "Replaces every occurrence of the search text."),
                fn("SUBSTR", "String", "(text, start, length)", "TEXT", "Slice of the text, counting from 1."));
    }

    private static List<RoutineMetadata> sqlite() {
        return List.of(
                agg("GROUP_CONCAT", "(expression, separator)", "TEXT", "Joins the values into one string."),
                agg("TOTAL", "(expression)", "REAL", "Like SUM but always returns a float and 0.0 on an empty set."),

                fn("IFNULL", "Conditional", "(value, fallback)", "any", "The fallback when the value is null."),
                fn("IIF", "Conditional", "(condition, ifTrue, ifFalse)", "any", "Chooses between two values based on a condition."),
                fn("TYPEOF", "Conversion", "(expression)", "TEXT", "Storage class of the value: null, integer, real, text or blob."),

                fn("DATE", "Date", "(timevalue, modifier, ...)", "TEXT", "Date as YYYY-MM-DD."),
                fn("TIME", "Date", "(timevalue, modifier, ...)", "TEXT", "Time as HH:MM:SS."),
                fn("DATETIME", "Date", "(timevalue, modifier, ...)", "TEXT", "Date and time as YYYY-MM-DD HH:MM:SS."),
                fn("JULIANDAY", "Date", "(timevalue, modifier, ...)", "REAL", "Julian day number for the given time."),
                fn("STRFTIME", "Date", "(format, timevalue, modifier, ...)", "TEXT", "Formats a date with a strftime pattern."),

                fn("INSTR", "String", "(text, search)", "INTEGER", "Position of the first occurrence, 0 when absent."),
                fn("HEX", "String", "(blob)", "TEXT", "Hexadecimal representation of the value."),
                fn("RANDOM", "Math", "()", "INTEGER", "Pseudo-random integer."),

                fn("JSON_EXTRACT", "JSON", "(json, path)", "any", "Value at the given JSON path."),
                fn("JSON_ARRAY", "JSON", "(value, ...)", "TEXT", "Builds a JSON array from the arguments."),
                fn("JSON_OBJECT", "JSON", "(key, value, ...)", "TEXT", "Builds a JSON object from the pairs."),

                fn("ROW_NUMBER", "Window", "() OVER (...)", "INTEGER", "Sequential number of the row inside its window."),
                fn("RANK", "Window", "() OVER (...)", "INTEGER", "Rank inside the window, with gaps after ties."),
                fn("DENSE_RANK", "Window", "() OVER (...)", "INTEGER", "Rank inside the window, without gaps."),
                fn("LAG", "Window", "(expression, offset) OVER (...)", "any", "Value from an earlier row of the window."),
                fn("LEAD", "Window", "(expression, offset) OVER (...)", "any", "Value from a later row of the window."));
    }

    private static List<RoutineMetadata> mysql() {
        return List.of(
                agg("GROUP_CONCAT", "(expression SEPARATOR ', ')", "TEXT", "Joins the values into one string."),
                agg("STDDEV_SAMP", "(expression)", "REAL", "Sample standard deviation."),
                agg("VAR_SAMP", "(expression)", "REAL", "Sample variance."),

                fn("IFNULL", "Conditional", "(value, fallback)", "any", "The fallback when the value is null."),
                fn("IF", "Conditional", "(condition, ifTrue, ifFalse)", "any", "Chooses between two values based on a condition."),

                fn("NOW", "Date", "()", "DATETIME", "Current date and time."),
                fn("CURDATE", "Date", "()", "DATE", "Current date."),
                fn("DATE_FORMAT", "Date", "(date, format)", "TEXT", "Formats a date with a MySQL pattern."),
                fn("DATEDIFF", "Date", "(date1, date2)", "INTEGER", "Number of days between two dates."),
                fn("DATE_ADD", "Date", "(date, INTERVAL n unit)", "DATETIME", "Adds an interval to a date."),
                fn("YEAR", "Date", "(date)", "INTEGER", "Year part of a date."),
                fn("MONTH", "Date", "(date)", "INTEGER", "Month part of a date."),

                fn("CONCAT", "String", "(text, ...)", "TEXT", "Joins the arguments into one string."),
                fn("CONCAT_WS", "String", "(separator, text, ...)", "TEXT", "Joins the arguments with a separator."),
                fn("LOCATE", "String", "(search, text)", "INTEGER", "Position of the first occurrence, 0 when absent."),
                fn("LPAD", "String", "(text, length, padding)", "TEXT", "Pads the text on the left up to a length."),

                fn("JSON_EXTRACT", "JSON", "(json, path)", "JSON", "Value at the given JSON path."),
                fn("JSON_UNQUOTE", "JSON", "(json)", "TEXT", "Removes the quotes from a JSON string."),

                fn("ROW_NUMBER", "Window", "() OVER (...)", "INTEGER", "Sequential number of the row inside its window."),
                fn("RANK", "Window", "() OVER (...)", "INTEGER", "Rank inside the window, with gaps after ties."),
                fn("LAG", "Window", "(expression, offset) OVER (...)", "any", "Value from an earlier row of the window."),
                fn("LEAD", "Window", "(expression, offset) OVER (...)", "any", "Value from a later row of the window."));
    }

    /**
     * O PostgreSQL descreve as suas próprias funções no {@code pg_proc}, por isso aqui só
     * ficam as mais usadas, para o catálogo ter conteúdo mesmo se a leitura falhar.
     */
    private static List<RoutineMetadata> postgresql() {
        return List.of(
                agg("STRING_AGG", "(expression, separator)", "TEXT", "Joins the values into one string."),
                agg("ARRAY_AGG", "(expression)", "ARRAY", "Collects the values into an array."),
                agg("STDDEV_SAMP", "(expression)", "REAL", "Sample standard deviation."),

                fn("NOW", "Date", "()", "TIMESTAMPTZ", "Current date and time with time zone."),
                fn("AGE", "Date", "(timestamp, timestamp)", "INTERVAL", "Interval between two timestamps."),
                fn("DATE_TRUNC", "Date", "(field, timestamp)", "TIMESTAMP", "Truncates a timestamp to the given precision."),
                fn("EXTRACT", "Date", "(field FROM timestamp)", "NUMERIC", "Pulls a field such as year or month out of a timestamp."),
                fn("TO_CHAR", "Conversion", "(value, format)", "TEXT", "Formats a number or date as text."),

                fn("POSITION", "String", "(search IN text)", "INTEGER", "Position of the first occurrence, 0 when absent."),
                fn("SPLIT_PART", "String", "(text, separator, index)", "TEXT", "Nth piece of the text split by a separator."),

                fn("JSONB_EXTRACT_PATH", "JSON", "(jsonb, path, ...)", "JSONB", "Value at the given JSON path."),
                fn("GENERATE_SERIES", "Set", "(start, stop, step)", "SETOF", "Produces a series of values as rows."),

                fn("ROW_NUMBER", "Window", "() OVER (...)", "BIGINT", "Sequential number of the row inside its window."),
                fn("RANK", "Window", "() OVER (...)", "BIGINT", "Rank inside the window, with gaps after ties."),
                fn("LAG", "Window", "(expression, offset) OVER (...)", "any", "Value from an earlier row of the window."),
                fn("LEAD", "Window", "(expression, offset) OVER (...)", "any", "Value from a later row of the window."));
    }

    private static List<RoutineMetadata> access() {
        return List.of(
                fn("IIF", "Conditional", "(condition, ifTrue, ifFalse)", "any", "Chooses between two values based on a condition."),
                fn("NZ", "Conditional", "(value, fallback)", "any", "The fallback when the value is null."),
                fn("DATE", "Date", "()", "DATE", "Current date."),
                fn("FORMAT", "String", "(value, format)", "TEXT", "Formats a value as text."),
                fn("MID", "String", "(text, start, length)", "TEXT", "Slice of the text, counting from 1."),
                fn("INSTR", "String", "(text, search)", "INTEGER", "Position of the first occurrence, 0 when absent."));
    }

}
