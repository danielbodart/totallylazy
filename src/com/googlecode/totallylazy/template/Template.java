package com.googlecode.totallylazy.template;

import com.googlecode.totallylazy.Callables;
import com.googlecode.totallylazy.Unchecked;
import com.googlecode.totallylazy.template.ast.Attribute;
import com.googlecode.totallylazy.template.ast.Grammar;
import com.googlecode.totallylazy.template.ast.FunctionCall;

import java.util.List;
import java.util.Map;

import static com.googlecode.totallylazy.Maps.map;
import static com.googlecode.totallylazy.Maps.pairs;
import static com.googlecode.totallylazy.Sequences.sequence;

public class Template implements Renderer<Map<String, Object>> {
    private final String template;
    private final TemplateGroup parent;

    private Template(String template, TemplateGroup parent) {
        this.parent = parent;
        this.template = template;
    }

    public static Template template(String template) {
        return template(template, EmptyTemplateGroup.Instance);}
    public static Template template(String template, TemplateGroup parent) {return new Template(template, parent);}

    @Override
    public Appendable render(Map<String, Object> context, Appendable appendable) throws Exception {
        return sequence(Grammar.TEMPLATE.parse(template).value()).
                fold(appendable, (a, node) -> append(node, context, a));
    }

    Appendable append(Object expression, Map<String, Object> context, Appendable appendable) throws Exception {
        if(expression instanceof CharSequence) return appendable.append(((CharSequence) expression));
        if(expression instanceof Attribute) return parent.render(context.get(((Attribute) expression).value()), appendable);
        if(expression instanceof FunctionCall){
            FunctionCall functionCall = (FunctionCall) expression;
            return parent.get(functionCall.name()).render(values(functionCall.arguments(), context), appendable);
        }
        throw new IllegalArgumentException("Unknown expression type: " + expression);
    }

    private Object values(Object arguments, Map<String, Object> context) throws Exception {
        if(arguments instanceof List) {
            List<?> args = (List<?>) arguments;
            if(args.isEmpty()) return context;
            if(args.size() == 1) return value(args.get(0), context);
            return sequence(args).map(arg -> value(arg, context)).toList();
        }
        if(arguments instanceof Map) {
            return map(pairs(Unchecked.<Map<String, Object>>cast(arguments)).map(Callables.<String, Object, Object>second(n -> value(n, context))));
        }
        throw new IllegalArgumentException("Unknown arguments type: " + arguments);
    }

    Object value(Object value, Map<String, Object> context) throws Exception {
        if(value instanceof CharSequence) return value;
        if(value instanceof Attribute) return context.get(((Attribute) value).value());
        if(value instanceof FunctionCall) {
            FunctionCall functionCall = (FunctionCall) value;
            return parent.get(functionCall.name()).render(values(functionCall.arguments(), context));
        }
        throw new IllegalArgumentException("Unknown value type: " + value);
    }

    @Override
    public String toString() {
        return template;
    }
}
