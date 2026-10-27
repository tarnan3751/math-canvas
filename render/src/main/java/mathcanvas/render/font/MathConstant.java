package mathcanvas.render.font;

/**
 * The constants of the OpenType MATH table, in the table's order and under its names. Each is in
 * font units, except the three percentages.
 */
public enum MathConstant {
  /** Script size as a percentage of the base size. */
  SCRIPT_PERCENT_SCALE_DOWN,
  /** Scriptscript size as a percentage of the base size. */
  SCRIPT_SCRIPT_PERCENT_SCALE_DOWN,
  /** The least height of a delimited subformula that makes its fences stretch. */
  DELIMITED_SUB_FORMULA_MIN_HEIGHT,
  /** The least height of a big operator in display style. */
  DISPLAY_OPERATOR_MIN_HEIGHT,
  /** White space between lines of math, unused here. */
  MATH_LEADING,
  /** The height of the math axis above the baseline. */
  AXIS_HEIGHT,
  /** The accent base height, unused until accents. */
  ACCENT_BASE_HEIGHT,
  /** The flattened accent base height, unused until accents. */
  FLATTENED_ACCENT_BASE_HEIGHT,
  /** The standard shift down of a subscript. */
  SUBSCRIPT_SHIFT_DOWN,
  /** The highest a subscript's top may be. */
  SUBSCRIPT_TOP_MAX,
  /** The least drop of a subscript's baseline below a base's bottom. */
  SUBSCRIPT_BASELINE_DROP_MIN,
  /** The standard shift up of a superscript. */
  SUPERSCRIPT_SHIFT_UP,
  /** The standard shift up of a superscript in a cramped style. */
  SUPERSCRIPT_SHIFT_UP_CRAMPED,
  /** The least height of a superscript's bottom. */
  SUPERSCRIPT_BOTTOM_MIN,
  /** The most a superscript's baseline may drop below a base's top. */
  SUPERSCRIPT_BASELINE_DROP_MAX,
  /** The least gap between a subscript and a superscript. */
  SUB_SUPERSCRIPT_GAP_MIN,
  /** The highest a superscript's bottom is pushed when a subscript is moved down for the gap. */
  SUPERSCRIPT_BOTTOM_MAX_WITH_SUBSCRIPT,
  /** Extra space after a script. */
  SPACE_AFTER_SCRIPT,
  /** The least gap between an upper limit and its operator. */
  UPPER_LIMIT_GAP_MIN,
  /** The least rise of an upper limit's baseline above its operator's top. */
  UPPER_LIMIT_BASELINE_RISE_MIN,
  /** The least gap between a lower limit and its operator. */
  LOWER_LIMIT_GAP_MIN,
  /** The least drop of a lower limit's baseline below its operator's bottom. */
  LOWER_LIMIT_BASELINE_DROP_MIN,
  /** Stack: top shift up, unused. */
  STACK_TOP_SHIFT_UP,
  /** Stack: top shift up in display style, unused. */
  STACK_TOP_DISPLAY_STYLE_SHIFT_UP,
  /** Stack: bottom shift down, unused. */
  STACK_BOTTOM_SHIFT_DOWN,
  /** Stack: bottom shift down in display style, unused. */
  STACK_BOTTOM_DISPLAY_STYLE_SHIFT_DOWN,
  /** Stack: least gap, unused. */
  STACK_GAP_MIN,
  /** Stack: least gap in display style, unused. */
  STACK_DISPLAY_STYLE_GAP_MIN,
  /** Stretch stack: top shift up, unused. */
  STRETCH_STACK_TOP_SHIFT_UP,
  /** Stretch stack: bottom shift down, unused. */
  STRETCH_STACK_BOTTOM_SHIFT_DOWN,
  /** Stretch stack: least gap above, unused. */
  STRETCH_STACK_GAP_ABOVE_MIN,
  /** Stretch stack: least gap below, unused. */
  STRETCH_STACK_GAP_BELOW_MIN,
  /** The standard shift up of a numerator. */
  FRACTION_NUMERATOR_SHIFT_UP,
  /** The standard shift up of a numerator in display style. */
  FRACTION_NUMERATOR_DISPLAY_STYLE_SHIFT_UP,
  /** The standard shift down of a denominator. */
  FRACTION_DENOMINATOR_SHIFT_DOWN,
  /** The standard shift down of a denominator in display style. */
  FRACTION_DENOMINATOR_DISPLAY_STYLE_SHIFT_DOWN,
  /** The least gap between a numerator's bottom and the bar. */
  FRACTION_NUMERATOR_GAP_MIN,
  /** The least gap between a numerator's bottom and the bar in display style. */
  FRACTION_NUM_DISPLAY_STYLE_GAP_MIN,
  /** The thickness of the fraction bar. */
  FRACTION_RULE_THICKNESS,
  /** The least gap between the bar and a denominator's top. */
  FRACTION_DENOMINATOR_GAP_MIN,
  /** The least gap between the bar and a denominator's top in display style. */
  FRACTION_DENOM_DISPLAY_STYLE_GAP_MIN,
  /** Skewed fraction: horizontal gap, unused. */
  SKEWED_FRACTION_HORIZONTAL_GAP,
  /** Skewed fraction: vertical gap, unused. */
  SKEWED_FRACTION_VERTICAL_GAP,
  /** Overbar: gap, unused. */
  OVERBAR_VERTICAL_GAP,
  /** Overbar: thickness, unused. */
  OVERBAR_RULE_THICKNESS,
  /** Overbar: extra ascender, unused. */
  OVERBAR_EXTRA_ASCENDER,
  /** Underbar: gap, unused. */
  UNDERBAR_VERTICAL_GAP,
  /** Underbar: thickness, unused. */
  UNDERBAR_RULE_THICKNESS,
  /** Underbar: extra descender, unused. */
  UNDERBAR_EXTRA_DESCENDER,
  /** The gap between a radicand and the radical's bar. */
  RADICAL_VERTICAL_GAP,
  /** The gap between a radicand and the radical's bar in display style. */
  RADICAL_DISPLAY_STYLE_VERTICAL_GAP,
  /** The thickness of the radical's bar. */
  RADICAL_RULE_THICKNESS,
  /** Extra space above the radical's bar. */
  RADICAL_EXTRA_ASCENDER,
  /** The space before a radical's index. */
  RADICAL_KERN_BEFORE_DEGREE,
  /** The space after a radical's index, negative to tuck it over the sign. */
  RADICAL_KERN_AFTER_DEGREE,
  /** The raise of an index's bottom as a percentage of the radical's height. */
  RADICAL_DEGREE_BOTTOM_RAISE_PERCENT
}
