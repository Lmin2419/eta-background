// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/accessibility/TextEditPlanner.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
/** 按 Android 文本选区（UTF-16 offset）生成一次真实“键入”后的内容与光标位置。 */
export interface TextEditPlan {
  text: string;
  cursor: number;
}
export function canSafelyReconstruct(
  password: boolean,
  textAvailable: boolean,
  textLength: number,
  selectionStart: number,
  selectionEnd: number,
): boolean {
  if (password || !textAvailable || textLength < 0) return false;
  const startValid = selectionStart >= 0 && selectionStart <= textLength;
  const endValid = selectionEnd >= 0 && selectionEnd <= textLength;
  if (startValid && endValid) return true;
  // 部分空输入控件以 -1 表示尚未创建光标；空文本仍只有插入点 0。
  return textLength === 0 && selectionStart <= 0 && selectionEnd <= 0;
}
export function insertAtSelection(
  currentText: string,
  insertedText: string,
  selectionStart: number,
  selectionEnd: number,
): TextEditPlan | null {
  const selectionIsValid =
    selectionStart >= 0 &&
    selectionStart <= currentText.length &&
    selectionEnd >= 0 &&
    selectionEnd <= currentText.length;
  if (!selectionIsValid && currentText !== '') return null;
  const start = selectionIsValid ? selectionStart : 0;
  const end = selectionIsValid ? selectionEnd : 0;
  const lower = Math.min(start, end);
  const upper = Math.max(start, end);
  return {
    text: currentText.substring(0, lower) + insertedText + currentText.substring(upper),
    cursor: lower + insertedText.length,
  };
}
