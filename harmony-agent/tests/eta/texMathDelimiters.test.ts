// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/markdown/TexMathDelimitersTest.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
import test from "node:test";
import assert from "node:assert/strict";
import { StreamingGfmParserSession } from "../../entry/src/main/ets/core/eta/markdown/streamingGfmParser";
import { normalizeTexMath } from "../../entry/src/main/ets/core/eta/markdown/texMathDelimiters";
import { hasSpan, paragraphOf } from "./markdownFixtures";
const blocks = (source: string) =>
  new StreamingGfmParserSession().parse(source, true).document.blocks;
test("inlineParenthesesKeepLatexSourceIntact", () => {
  const paragraph = paragraphOf("行内 \\(a_i + b_j = \\frac{1}{2}\\) 结束");
  const formula = "a_i + b_j = \\frac{1}{2}";
  assert.ok(paragraph.text.text.includes(formula));
  assert.ok(hasSpan(paragraph.text, formula, "code"));
});
test("displayBracketsBecomeMathBlockWithoutLosingEscapes", () => {
  const result = blocks("\\[\n\\sum_{i=1}^{n} a_i = \\{x \\mid x > 0\\}\n\\]");
  assert.equal(result.length, 1);
  const math = result[0];
  assert.equal(math.kind, "code");
  if (math.kind === "code") {
    assert.ok(math.isMath);
    assert.equal(math.text.text, "\\sum_{i=1}^{n} a_i = \\{x \\mid x > 0\\}");
  }
});
test("singleLineDisplayBracketsAreMath", () => {
  const result = blocks("推导如下：\n\n\\[ E = mc^2 \\]");
  const math = result[result.length - 1];
  assert.equal(math.kind, "code");
  if (math.kind === "code") {
    assert.ok(math.isMath);
    assert.equal(math.text.text, "E = mc^2");
  }
});
test("normalizationPreservesOffsets", () => {
  const source = "a \\(x\\) b\n\n\\[\ny\n\\]";
  assert.equal(normalizeTexMath(source).length, source.length);
});
test("escapedMarkdownBracketsAndCodeAreNotMath", () => {
  for (const source of [
    "数组 arr\\[0\\] 的值",
    "`\\(x\\)` 是行内代码",
    "```latex\n\\[\nx\n\\]\n```",
    "转义反斜杠 \\\\(x\\\\) 不是公式",
    "未闭合 \\(x 后面没有了",
    "跨空行 \\(x\n\ny\\) 不是公式",
  ])
    assert.equal(normalizeTexMath(source), source);
});
test(
  "fencedBlankLinesPreserveCodeAndNormalizeSurroundingMath",
  { timeout: 1000 },
  () => {
    for (const fence of ["```", "~~~"])
      for (const newline of ["\n", "\r\n"]) {
        const code = ["\\(a_i\\)", "", "", " ", "\t", "\\[", "b_j", "\\]"].join(
          newline,
        );
        const fenced = `${fence}latex${newline}${code}${newline}${fence}`;
        assert.equal(normalizeTexMath(fenced), fenced);
        assert.equal(
          normalizeTexMath(
            `\\(before\\)${newline}${fenced}${newline}\\[after\\]`,
          ),
          `$$before$$${newline}${fenced}${newline}$$after$$`,
        );
      }
  },
);
test("unclosedFencesPreserveBlankLinesThroughEof", { timeout: 1000 }, () => {
  for (const fence of ["```", "~~~"])
    for (const ending of ["", "\n", "\n\n"]) {
      const source = `${fence}latex\n\n\n\\(literal\\)${ending}`;
      assert.equal(normalizeTexMath(source), source);
    }
});
test(
  "shortOrMismatchedFencesDoNotExposeCodeToMathNormalization",
  { timeout: 1000 },
  () => {
    for (const [fence, otherFence] of [
      ["````", "~~~"],
      ["~~~~", "```"],
    ]) {
      const code = `${fence}latex\n\n${fence.slice(0, -1)}\n\\(a_i\\)\n\n${otherFence}\n\\[b_j\\]\n\n${fence}${fence[0]}`;
      assert.equal(
        normalizeTexMath(`${code}\n\\(after\\)`),
        `${code}\n$$after$$`,
      );
    }
  },
);
test("unmatchedBacktickDoesNotHideLaterFormula", () => {
  assert.equal(
    normalizeTexMath("单个 ` 反引号，然后 \\(x\\)"),
    "单个 ` 反引号，然后 $$x$$",
  );
});
test("dollarMathAndCurrencyStayUnchanged", () => {
  for (const source of ["价格 $4 和 $20 每百万", "行内 $E=mc^2$ 结束"])
    assert.equal(normalizeTexMath(source), source);
  assert.ok(
    !paragraphOf("价格 $4 和 $20 每百万").text.spans.some(
      (span) => span.kind === "code",
    ),
  );
});
test("streamingUnclosedDisplayMathConvertsOnceClosed", () => {
  const session = new StreamingGfmParserSession();
  const partial = session.parse("\\[\n\\frac{a}{b}", false).document.blocks;
  assert.ok(!partial.some((block) => block.kind === "code" && block.isMath));
  const complete = session.parse("\\[\n\\frac{a}{b}\n\\]", true).document
    .blocks;
  assert.equal(complete.length, 1);
  assert.equal(complete[0].kind, "code");
  if (complete[0].kind === "code")
    assert.equal(complete[0].text.text, "\\frac{a}{b}");
});
test(
  "streamingFencedBlankLinesKeepCodeAndResumeMathAfterClosing",
  { timeout: 5000 },
  () => {
    for (const fence of ["```", "~~~"]) {
      const session = new StreamingGfmParserSession();
      const code = "\\(a_i\\)\n\n\n\\[\nb_j\n\\]";
      const openSource = `前 \\(before\\)\n\n${fence}latex\n${code}`;
      const source = `${openSource}\n${fence}\n\n\\[after\\]`;
      for (let end = 0; end <= source.length; end++) {
        const prefix = source.slice(0, end);
        const partial = session.parse(prefix, false);
        assert.equal(partial.originalSource, prefix);
        if (end === openSource.length) {
          const partialCode = partial.document.blocks.filter(
            (block) => block.kind === "code",
          );
          assert.equal(partialCode.length, 1);
          assert.ok(!partialCode[0].isMath);
          assert.equal(partialCode[0].text.text, code);
        }
      }
      const complete = session.parse(source, true);
      assert.equal(complete.originalSource, source);
      assert.equal(
        complete.renderedSource,
        `前 $$before$$\n\n${fence}latex\n${code}\n${fence}\n\n$$after$$`,
      );
      const codeBlocks = complete.document.blocks.filter(
        (block) => block.kind === "code",
      );
      const fenced = codeBlocks.filter((block) => !block.isMath);
      assert.equal(fenced.length, 1);
      assert.equal(fenced[0].language, "latex");
      assert.equal(fenced[0].text.text, code);
      assert.equal(fenced[0].offset, source.indexOf(fence));
      const math = codeBlocks.filter((block) => block.isMath);
      assert.equal(math.length, 1);
      assert.equal(math[0].text.text, "after");
      assert.equal(math[0].offset, source.indexOf("\\[after\\]"));
    }
  },
);
