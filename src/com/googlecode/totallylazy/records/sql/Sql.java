package com.googlecode.totallylazy.records.sql;

import com.googlecode.totallylazy.*;
import com.googlecode.totallylazy.comparators.AscendingComparator;
import com.googlecode.totallylazy.comparators.DescendingComparator;
import com.googlecode.totallylazy.numbers.Sum;
import com.googlecode.totallylazy.numbers.Average;
import com.googlecode.totallylazy.predicates.AndPredicate;
import com.googlecode.totallylazy.predicates.Between;
import com.googlecode.totallylazy.predicates.ContainsPredicate;
import com.googlecode.totallylazy.predicates.EndsWithPredicate;
import com.googlecode.totallylazy.predicates.EqualsPredicate;
import com.googlecode.totallylazy.predicates.GreaterThan;
import com.googlecode.totallylazy.predicates.GreaterThanOrEqualTo;
import com.googlecode.totallylazy.predicates.InPredicate;
import com.googlecode.totallylazy.predicates.LessThan;
import com.googlecode.totallylazy.predicates.LessThanOrEqualTo;
import com.googlecode.totallylazy.predicates.Not;
import com.googlecode.totallylazy.predicates.NotNullPredicate;
import com.googlecode.totallylazy.predicates.NullPredicate;
import com.googlecode.totallylazy.predicates.OrPredicate;
import com.googlecode.totallylazy.predicates.StartsWithPredicate;
import com.googlecode.totallylazy.predicates.WherePredicate;
import com.googlecode.totallylazy.records.*;

import java.util.Comparator;

import static com.googlecode.totallylazy.Callables.first;
import static com.googlecode.totallylazy.Pair.pair;
import static com.googlecode.totallylazy.Sequences.empty;
import static com.googlecode.totallylazy.Sequences.repeat;
import static com.googlecode.totallylazy.Sequences.sequence;

public class Sql {
    @SuppressWarnings("unchecked")
    public Pair<String, Sequence<Object>> whereClause(Sequence<Predicate<? super Record>> where) {
        if (where.isEmpty()) return pair("", empty());
        final Sequence<Pair<String, Sequence<Object>>> sqlAndValues = where.map(toSql());
        return pair("where " + sqlAndValues.map(first(String.class)).toString(" "), sqlAndValues.flatMap(values()));
    }

    public String orderByClause(Option<Comparator<? super Record>> comparator) {
        return comparator.map(new Callable1<Comparator<? super Record>, String>() {
            public String call(Comparator<? super Record> comparator) throws Exception {
                return "order by " + toSql(comparator);
            }
        }).getOrElse("");
    }


    public String toSql(Comparator<? super Record> comparator) {
        if (comparator instanceof AscendingComparator) {
            return toSql(((AscendingComparator<? super Record, ?>) comparator).callable()).first() + " asc ";
        }
        if (comparator instanceof DescendingComparator) {
            return toSql(((DescendingComparator<? super Record, ?>) comparator).callable()).first() + " desc ";
        }
        throw new UnsupportedOperationException("Unsupported comparator " + comparator);
    }

    public boolean isSupported(Predicate<? super Record> predicate) {
        try {
            toSql(predicate);
            return true;
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }

    public <T> Pair<String, Sequence<Object>> toSql(Callable1<? super Record, T> callable) {
        if (callable instanceof Keyword) {
            return pair(callable.toString(), empty());
        }
        if (callable instanceof SelectCallable) {
            return pair(sequence(((SelectCallable) callable).keywords()).toString("", ",", ""), empty());
        }
        throw new UnsupportedOperationException("Unsupported callable " + callable);
    }

    @SuppressWarnings("unchecked")
    public Pair<String, Sequence<Object>> toSql(Predicate predicate) {
        if (predicate instanceof WherePredicate) {
            WherePredicate wherePredicate = (WherePredicate) predicate;
            final Pair<String, Sequence<Object>> pair = toSql(wherePredicate.predicate());
            return pair(toSql(wherePredicate.callable()).first() + " " + pair.first(), pair.second());
        }
        if (predicate instanceof AndPredicate) {
            AndPredicate andPredicate = (AndPredicate) predicate;
            final Sequence<Pair<String, Sequence<Object>>> pairs = sequence(andPredicate.predicates()).map(toSql());
            return pair("( " + pairs.map(first(String.class)).toString("and ") + " ) ", pairs.flatMap(values()));
        }
        if (predicate instanceof OrPredicate) {
            OrPredicate andPredicate = (OrPredicate) predicate;
            final Sequence<Pair<String, Sequence<Object>>> pairs = sequence(andPredicate.predicates()).map(toSql());
            return pair("( " + pairs.map(first(String.class)).toString("or ") + " ) ", pairs.flatMap(values()));
        }
        if (predicate instanceof NullPredicate) {
            return pair(" is null ", Sequences.<Object>empty());
        }
        if (predicate instanceof NotNullPredicate) {
            return pair(" is not null ", Sequences.<Object>empty());
        }
        if (predicate instanceof EqualsPredicate) {
            return pair("= ? ", getValue(predicate));
        }
        if (predicate instanceof Not) {
            return pair("<> ? ", sequence(toSql(((Not) predicate).predicate()).second()));
        }
        if (predicate instanceof GreaterThan) {
            return pair("> ? ", getValue(predicate));
        }
        if (predicate instanceof GreaterThanOrEqualTo) {
            return pair(">= ? ", getValue(predicate));
        }
        if (predicate instanceof LessThan) {
            return pair("< ? ", getValue(predicate));
        }
        if (predicate instanceof LessThanOrEqualTo) {
            return pair("<= ? ", getValue(predicate));
        }
        if (predicate instanceof Between) {
            Between between = (Between) predicate;
            return pair("between ? and ? ", sequence(between.lower(), between.upper()));
        }
        if (predicate instanceof InPredicate) {
            InPredicate inPredicate = (InPredicate) predicate;
            Sequence sequence = inPredicate.values();
            if (sequence instanceof QuerySequence) {
                ParameterisedExpression pair = ((QuerySequence) sequence).query().parameterisedExpression();
                return pair("in ( " + pair.expression() + ")", pair.parameters());
            }
            return pair(repeat("?").take((Integer) inPredicate.values().size()).toString("in (", ",", ")"), (Sequence<Object>) sequence);
        }
        if (predicate instanceof StartsWithPredicate) {
            return pair("like ? ", sequence((Object) (((StartsWithPredicate) predicate).value() + "%%")));
        }
        if (predicate instanceof EndsWithPredicate) {
            return pair("like ? ", sequence((Object) ("%%" + ((EndsWithPredicate) predicate).value())));
        }
        if (predicate instanceof ContainsPredicate) {
            return pair("like ? ", sequence((Object) ("%%" + ((ContainsPredicate) predicate).value() + "%%")));
        }
        throw new UnsupportedOperationException("Unsupported predicate " + predicate);
    }

    private Sequence<Object> getValue(Predicate predicate) {
        return sequence(((Value) predicate).value());
    }

    public Callable1<? super Pair<String, Sequence<Object>>, Iterable<Object>> values() {
        return new Callable1<Pair<String, Sequence<Object>>, Iterable<Object>>() {
            public Iterable<Object> call(Pair<String, Sequence<Object>> pair) throws Exception {
                return pair.second();
            }
        };
    }

    public Callable1<? super Predicate, Pair<String, Sequence<Object>>> toSql() {
        return new Callable1<Predicate, Pair<String, Sequence<Object>>>() {
            public Pair<String, Sequence<Object>> call(Predicate predicate) throws Exception {
                return toSql(predicate);
            }
        };
    }

    public boolean isSupported(Comparator<? super Record> comparator) {
        try {
            toSql(comparator);
            return true;
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }

    public String asSql(Aggregates aggregates) {
        return sequence(aggregates.value()).map(new Callable1<Aggregate, String>() {
            public String call(Aggregate aggregate) throws Exception {
                return asSql(aggregate);
            }
        }).toString();
    }

    public String asSql(Aggregate aggregate) {
        return toSql(aggregate.callable(), aggregate.source().name()) + " as " + aggregate.name();
    }

    public boolean isSupported(Callable2<?, ?, ?> callable) {
        try{
            if(callable instanceof Aggregates){
                asSql((Aggregates) callable);
                return true;
            }
            return false;
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }

    private String toSql(Callable2<?, ?, ?> callable, String column) {
        if(callable instanceof CountNotNull){
            return String.format("count(%s)", column);
        }
        if(callable instanceof Average){
            return String.format("avg(%s)", column);
        }
        if(callable instanceof Sum){
            return String.format("sum(%s)", column);
        }
        if(callable instanceof Minimum){
            return String.format("min(%s)", column);
        }
        if(callable instanceof Maximum){
            return String.format("max(%s)", column);
        }
        throw new UnsupportedOperationException();
    }

}
