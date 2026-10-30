// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/app/LocaleResourcesTest.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
// Port adaptation: ETA_PORT §8 explicitly makes Chinese the default, so fr-FR falls back to Chinese.
// These inspect generated resources, not HarmonyOS ResourceManager locale selection on a device.
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
function resourceFile(locale: string, kind: string): string {
  const folder = locale.startsWith('en') ? 'en_US' : ['zh-TW', 'zh-HK'].includes(locale) ? 'zh_Hant' : 'base';
  return resolve('entry/src/main/resources', folder, 'element', kind + '.json');
}
function localizedString(locale: string, key: string): string {
  const values = JSON.parse(readFileSync(resourceFile(locale, 'string'), 'utf8')).string as { name: string; value: string }[];
  const row = values.find(v => v.name === key); assert.ok(row, key); return row.value;
}
function localizedQuantity(locale: string, key: string, n: number): string {
  const values = JSON.parse(readFileSync(resourceFile(locale, 'plural'), 'utf8')).plural as { name: string; value: { quantity: string; value: string }[] }[];
  const row = values.find(v => v.name === key); assert.ok(row, key);
  const form = row.value.find(v => v.quantity === (n === 1 ? 'one' : 'other')) ?? row.value.find(v => v.quantity === 'other');
  assert.ok(form); return form.value.replace(/%(?:1\$)?d/g, String(n));
}
test('supportedLocalesSelectExpectedResourcesAndOthersFallBackToEnglish (Harmony: Chinese default)', () => {
  assert.equal(localizedString('en-US', 'route_settings'), 'Settings');
  assert.equal(localizedString('zh-CN', 'route_settings'), '设置');
  assert.equal(localizedString('zh-SG', 'route_settings'), '设置');
  assert.equal(localizedString('zh-TW', 'route_settings'), '設定');
  assert.equal(localizedString('zh-HK', 'route_settings'), '設定');
  assert.equal(localizedString('zh-TW', 'time_yesterday'), '昨日');
  assert.equal(localizedString('fr-FR', 'route_settings'), '设置');
  assert.equal(localizedString('en-US', 'appearance_title'), 'Appearance & Theme');
  assert.equal(localizedString('zh-CN', 'appearance_title'), '外观与主题');
  assert.equal(localizedString('zh-TW', 'appearance_title'), '外觀與主題');
  assert.equal(localizedString('fr-FR', 'appearance_title'), '外观与主题');
  assert.equal(localizedQuantity('en-US', 'provider_models_count', 1), '1 model');
  assert.equal(localizedQuantity('en-US', 'provider_models_count', 2), '2 models');
});
test('supplementalUiResourcesFollowLocaleWhileTechnicalTermsStayEnglish', () => {
  assert.equal(localizedString('en-US', 'capability_current_device'), 'Current device');
  assert.equal(localizedString('zh-CN', 'capability_current_device'), '当前设备');
  assert.equal(localizedString('zh-TW', 'capability_current_device'), '目前裝置');
  assert.equal(localizedString('fr-FR', 'capability_current_device'), '当前设备');
  assert.equal(localizedString('en-US', 'execution_title'), '随身助手 is running');
  assert.equal(localizedString('zh-CN', 'execution_title'), '随身助手 正在运行');
  assert.equal(localizedString('zh-TW', 'execution_title'), '随身助手 正在執行');
  assert.equal(localizedString('en-US', 'linux_environment_configuration'), 'Environment configuration');
  assert.equal(localizedString('zh-CN', 'linux_environment_configuration'), '环境配置');
  assert.equal(localizedString('zh-TW', 'linux_environment_configuration'), '環境設定');
  assert.equal(localizedString('en-US', 'ui_view_description'), 'View description');
  assert.equal(localizedString('zh-CN', 'ui_view_description'), '查看说明');
  assert.equal(localizedString('zh-TW', 'ui_view_description'), '查看說明');
  assert.equal(localizedString('en-US', 'system_notice_model_retry'), 'Retrying model request');
  assert.equal(localizedString('zh-CN', 'system_notice_model_retry'), '模型请求重试');
  assert.equal(localizedString('zh-TW', 'system_notice_model_retry'), '模型請求重試');
  assert.equal(localizedQuantity('en-US', 'execution_summary', 1), '1 task is running. Return to 随身助手 to check progress.');
  assert.equal(localizedQuantity('en-US', 'execution_summary', 2), '2 tasks are running. Return to 随身助手 to check progress.');
  assert.equal(localizedQuantity('zh-CN', 'capability_workspace_partial_import', 1), '已导入 1 个文件，其余文件无法读取或超过 32 MiB');
  assert.equal(localizedQuantity('zh-TW', 'capability_workspace_partial_import', 2), '已匯入 2 個檔案，其餘檔案無法讀取或超過 32 MiB');
  for (const locale of ['en-US', 'zh-CN', 'zh-TW', 'fr-FR']) {
    assert.equal(localizedString(locale, 'route_skills'), 'Skills');
    assert.equal(localizedString(locale, 'speech_api_key'), 'API Key');
  }
});
