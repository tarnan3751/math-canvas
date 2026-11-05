package mathcanvas.engine.rule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The rewriter's work, counted while counting is on: how many times each rule was tried (matched
 * against a node) and how many rewrites it made, over every {@link Rewriter} call made in that
 * time, whatever the rule set. The CLI's {@code --stats} turns it on for a run; nothing else does,
 * and while it is off the rewriter's only cost is a null check per rule tried. A rule is named by
 * its rule set and its own name; the simplifier's rules run as one combined set, {@code standard}
 * ({@code Simplifier.COMBINED}).
 *
 * <p>The counts are taken by every thread that rewrites while counting is on, under this object's
 * lock; they are meant for one run at a time, as the CLI makes. A rewriter created before {@link
 * #stop}, on another thread, keeps counting into the counts it read until its call returns.
 */
public final class RewriteCounts {

  /**
   * One rule's counts.
   *
   * @param ruleSet the rule set it was tried in
   * @param rule its name
   * @param tries the times it was tried
   * @param rewrites the rewrites it made
   */
  public record Entry(String ruleSet, String rule, long tries, long rewrites) {}

  private record Key(String ruleSet, String rule) {}

  private static volatile RewriteCounts active;

  private final Map<Key, long[]> byRule = new HashMap<>();
  private long tries;
  private long rewrites;

  private RewriteCounts() {}

  /**
   * Turns counting on, with counts of zero, replacing any counts already being taken.
   *
   * @return the counts, which go on growing until {@link #stop}
   */
  public static RewriteCounts start() {
    RewriteCounts counts = new RewriteCounts();
    active = counts;
    return counts;
  }

  /** Turns counting off; the counts {@link #start} returned keep their values. */
  public static void stop() {
    active = null;
  }

  /** The counts being taken, or null when counting is off: what a rewriter reads once. */
  static RewriteCounts active() {
    return active;
  }

  synchronized void tried(String ruleSet, String rule) {
    tries++;
    byRule.computeIfAbsent(new Key(ruleSet, rule), k -> new long[2])[0]++;
  }

  synchronized void rewrote(String ruleSet, String rule) {
    rewrites++;
    byRule.computeIfAbsent(new Key(ruleSet, rule), k -> new long[2])[1]++;
  }

  /**
   * The rule tries in all.
   *
   * @return the count
   */
  public synchronized long tries() {
    return tries;
  }

  /**
   * The rewrites in all.
   *
   * @return the count
   */
  public synchronized long rewrites() {
    return rewrites;
  }

  /**
   * The rules tried most, most first, ties by rule set and then rule name.
   *
   * @param n the most entries to give
   * @return at most {@code n} entries
   */
  public synchronized List<Entry> mostTried(int n) {
    List<Entry> out = new ArrayList<>(byRule.size());
    byRule.forEach((k, v) -> out.add(new Entry(k.ruleSet(), k.rule(), v[0], v[1])));
    out.sort(
        Comparator.comparingLong(Entry::tries)
            .reversed()
            .thenComparing(Entry::ruleSet)
            .thenComparing(Entry::rule));
    return List.copyOf(out.subList(0, Math.min(n, out.size())));
  }
}
