import { ConversationRepository } from '../conversations';
import { DraftRepository } from '../drafts';
import { ProfileRepository } from '../profiles';
import { FoundationController } from './controller';
import { HttpTransport } from '../model';

/** Preview-only data and transport. The production entry does not import this module. */
export function previewController(): FoundationController {
  let counter = 0;
  const memory = (initial = '') => { let raw = initial; return { read: async () => raw, write: async (value: string) => { raw = value; } }; };
  const profiles = new ProfileRepository(memory(JSON.stringify({ selectedId: 'preview', profiles: [{ id: 'preview', name: '示例模型服务', baseUrl: 'https://preview.example/v1', model: '示例模型', models: ['示例模型', '示例推理模型'], streaming: true, screenshots: false, allowInsecureHttp: false }] })), { put: async () => {}, get: async () => '', remove: async () => {} }, () => 'p' + ++counter);
  let controller: FoundationController;
  const chats = new ConversationRepository(memory(), () => 'c' + ++counter, Date.now, () => controller?.changed(), 2), drafts = new DraftRepository(memory());
  const transport = (): HttpTransport => {
    let cancelled = false;
    return { cancel: () => { cancelled = true; }, post: async () => JSON.stringify({ choices: [{ message: { role: 'assistant', content: 'OK' }, finish_reason: 'stop' }] }),
      get: async () => JSON.stringify({ data: ['示例模型', '示例推理模型', '新增模型 A', '新增模型 B', '新增模型 C'].map(id => ({ id })) }),
      stream: async (_url, _headers, _body, chunk) => {
        for (const text of ['这是预览数据。', '\n\n', '回复会逐段显示，', '也可以点击停止。', '\n\n**可以继续切换会话、编辑模型或查看侧栏。**', '\n\n1. 列表第一项\n', '2. 列表第二项']) {
          await new Promise(resolve => setTimeout(resolve, 300)); if (cancelled) throw new Error('CANCELLED');
          chunk('data: ' + JSON.stringify({ choices: [{ delta: { content: text }, finish_reason: null }] }) + '\n\n');
        }
        chunk('data: ' + JSON.stringify({ choices: [{ delta: {}, finish_reason: 'stop' }] }) + '\n\ndata: [DONE]\n\n');
      }
    };
  };
  controller = new FoundationController({ chats, profiles, drafts, transport, uuid: () => 'r' + ++counter, load: async () => { await profiles.load(); await chats.load(); await drafts.load(); } });
  return controller;
}
