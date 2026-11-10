package pzcloud;

import java.io.IOException;
import java.util.*;

/** Small strict JSON codec; no runtime dependencies. Input size is bounded by callers. */
public final class Json {
    private Json() {}
    public static Object parse(String text) throws IOException {
        Parser p = new Parser(text);
        Object result = p.value(0);
        p.space();
        if (p.i != text.length()) throw new IOException("JSON: trailing content");
        return result;
    }
    @SuppressWarnings("unchecked")
    public static Map<String,Object> object(Object value) throws IOException {
        if (!(value instanceof Map<?,?>)) throw new IOException("Expected JSON object");
        return (Map<String,Object>) value;
    }
    @SuppressWarnings("unchecked")
    public static List<Object> array(Object value) throws IOException {
        if (!(value instanceof List<?>)) throw new IOException("Expected JSON array");
        return (List<Object>) value;
    }
    public static String str(Map<String,Object> obj, String key) { return Objects.toString(obj.get(key), ""); }
    public static long number(Map<String,Object> obj, String key) throws IOException {
        try { return Long.parseLong(str(obj, key)); } catch (NumberFormatException e) { throw new IOException("Invalid number: " + key); }
    }
    public static String encode(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String s) {
            StringBuilder out = new StringBuilder("\"");
            for (char c : s.toCharArray()) {
                switch(c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> { if(c < 32) out.append(String.format("\\u%04x", (int)c)); else out.append(c); }
                }
            }
            return out.append('"').toString();
        }
        if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
        if (obj instanceof Map<?,?> map) {
            StringJoiner j = new StringJoiner(",", "{", "}");
            map.forEach((k,v) -> j.add(encode(k.toString()) + ":" + encode(v)));
            return j.toString();
        }
        if (obj instanceof Collection<?> list) {
            StringJoiner j = new StringJoiner(",", "[", "]");
            list.forEach(v -> j.add(encode(v)));
            return j.toString();
        }
        throw new IllegalArgumentException("Unsupported JSON value");
    }
    private static final class Parser {
        final String text; int i;
        Parser(String text) { this.text = text; }
        void space() { while(i < text.length() && " \t\r\n".indexOf(text.charAt(i)) >= 0) i++; }
        char take() throws IOException { if(i == text.length()) throw new IOException("JSON: unexpected end"); return text.charAt(i++); }
        Object value(int depth) throws IOException {
            if(depth > 64) throw new IOException("JSON too deeply nested");
            space(); char c = take();
            if(c == '"') return string();
            if(c == '{') {
                Map<String,Object> obj = new LinkedHashMap<>(); space();
                if(i < text.length() && text.charAt(i) == '}') { i++; return obj; }
                do {
                    space(); if(take() != '"') throw new IOException("JSON: expected key");
                    String key = string(); space(); if(take() != ':') throw new IOException("JSON: expected colon");
                    if(obj.containsKey(key)) throw new IOException("JSON: duplicate key");
                    obj.put(key, value(depth + 1)); space(); c = take();
                    if(c == '}') return obj;
                    if(c != ',') throw new IOException("JSON: expected comma");
                } while(true);
            }
            if(c == '[') {
                List<Object> list = new ArrayList<>(); space();
                if(i < text.length() && text.charAt(i) == ']') { i++; return list; }
                do {
                    list.add(value(depth + 1)); space(); c = take();
                    if(c == ']') return list;
                    if(c != ',') throw new IOException("JSON: expected comma");
                } while(true);
            }
            i--; int start = i;
            while(i < text.length() && ",]} \t\r\n".indexOf(text.charAt(i)) < 0) i++;
            String word = text.substring(start, i);
            return switch(word) {
                case "null" -> null;
                case "true" -> true;
                case "false" -> false;
                default -> {
                    if(!word.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) throw new IOException("Invalid JSON token");
                    try {
                        if(word.contains(".") || word.contains("e") || word.contains("E")) yield Double.parseDouble(word);
                        yield Long.parseLong(word);
                    } catch(NumberFormatException ex) { throw new IOException("Invalid JSON number"); }
                }
            };
        }
        String string() throws IOException {
            StringBuilder s = new StringBuilder();
            while(true) {
                char c = take();
                if(c == '"') return s.toString();
                if(c < 32) throw new IOException("JSON: control character");
                if(c == '\\') {
                    c = take();
                    switch(c) {
                        case '"', '\\', '/' -> s.append(c);
                        case 'b' -> s.append('\b');
                        case 'f' -> s.append('\f');
                        case 'n' -> s.append('\n');
                        case 'r' -> s.append('\r');
                        case 't' -> s.append('\t');
                        case 'u' -> {
                            if(i + 4 > text.length()) throw new IOException("Invalid unicode escape");
                            try { s.append((char)Integer.parseInt(text.substring(i,i+4), 16)); }
                            catch(NumberFormatException ex) { throw new IOException("Invalid unicode escape"); }
                            i += 4;
                        }
                        default -> throw new IOException("Invalid JSON escape");
                    }
                } else s.append(c);
            }
        }
    }
}
