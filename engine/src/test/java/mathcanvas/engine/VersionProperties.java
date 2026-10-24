package mathcanvas.engine;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class VersionProperties {

  @Property
  void parseInvertsToString(@ForAll("versions") Version version) {
    assertThat(Version.parse(version.toString())).isEqualTo(version);
  }

  @Property
  void compareToIsConsistentWithEquals(
      @ForAll("versions") Version left, @ForAll("versions") Version right) {
    assertThat(left.compareTo(right) == 0).isEqualTo(left.equals(right));
    assertThat(Integer.signum(left.compareTo(right)))
        .isEqualTo(-Integer.signum(right.compareTo(left)));
  }

  @Property
  void compareToFollowsComponentOrder(
      @ForAll("versions") Version left, @ForAll("versions") Version right) {
    int components =
        Integer.compare(left.major(), right.major()) != 0
            ? Integer.compare(left.major(), right.major())
            : Integer.compare(left.minor(), right.minor()) != 0
                ? Integer.compare(left.minor(), right.minor())
                : Integer.compare(left.patch(), right.patch());
    int order = Integer.signum(left.compareTo(right));
    if (components != 0) {
      assertThat(order).isEqualTo(Integer.signum(components));
    } else if (left.qualifier().isEmpty() != right.qualifier().isEmpty()) {
      assertThat(order).isEqualTo(left.qualifier().isEmpty() ? 1 : -1);
    }
  }

  @Property
  void compareToIsTransitive(
      @ForAll("versions") Version a, @ForAll("versions") Version b, @ForAll("versions") Version c) {
    if (a.compareTo(b) <= 0 && b.compareTo(c) <= 0) {
      assertThat(a.compareTo(c)).isLessThanOrEqualTo(0);
    }
  }

  /** Small components and identifiers, so equal and near-equal pairs come up often. */
  @Provide
  Arbitrary<Version> versions() {
    Arbitrary<Integer> component =
        Arbitraries.oneOf(
            Arbitraries.integers().between(0, 3), Arbitraries.integers().between(0, 1_000_000));
    Arbitrary<String> numeric =
        Arbitraries.oneOf(
            Arbitraries.longs().between(0, 12).map(String::valueOf),
            Arbitraries.longs().between(0, Long.MAX_VALUE).map(String::valueOf));
    Arbitrary<String> alphanumeric =
        Arbitraries.oneOf(
            Arbitraries.of("alpha", "beta", "rc", "SNAPSHOT", "a-b", "0a", "-"),
            Arbitraries.strings()
                .withChars("0123456789abzABZ-")
                .ofMinLength(1)
                .ofMaxLength(6)
                .filter(s -> !s.chars().allMatch(Character::isDigit)));
    Arbitrary<String> qualifier =
        Arbitraries.oneOf(numeric, alphanumeric)
            .list()
            .ofMaxSize(3)
            .map(identifiers -> String.join(".", identifiers));
    return Combinators.combine(component, component, component, qualifier).as(Version::new);
  }
}
