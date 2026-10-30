import { common, Want } from '@kit.AbilityKit';
import { util } from '@kit.ArkTS';
import { ConversationRepository } from '../core/conversations';
import { DraftRepository } from '../core/drafts';
import { ProfileRepository } from '../core/profiles';
import { TaskLedger } from '../core/runtime';
import { FoundationController, FoundationDependencies } from '../core/foundation/controller';
import { RuntimeDatabase, DatabaseConversationStorage, DatabaseDocumentStorage, DatabaseProfileStorage } from './RuntimeStorage';
import { AssetSecretStorage } from './ProfileStore';
import { HarmonyHttpTransport } from './ModelHttp';
import { HttpTransport } from '../core/model';

/** A small platform seam: database, secure keys and HTTP. No legacy GUI/background/extension startup. */
export class FoundationHost {
  private static instance: FoundationHost | undefined;
  static get(context: common.UIAbilityContext): FoundationHost {
    if (!FoundationHost.instance) FoundationHost.instance = new FoundationHost(context);
    return FoundationHost.instance;
  }
  readonly controller: FoundationController;
  private loaded = false;
  private loading: Promise<void> | undefined;
  private ledger: TaskLedger;
  constructor(context: common.UIAbilityContext) {
    const db = new RuntimeDatabase(context);
    const uuid = (): string => util.generateRandomUUID();
    const chats = new ConversationRepository(new DatabaseConversationStorage(db, context.filesDir), uuid, Date.now, (): void => { this.controller?.changed(); }, 2);
    const profiles = new ProfileRepository(new DatabaseProfileStorage(db, context.filesDir), new AssetSecretStorage(), uuid);
    const drafts = new DraftRepository(new DatabaseDocumentStorage(db, 'drafts'));
    this.ledger = new TaskLedger(new DatabaseDocumentStorage(db, 'tasks'), uuid);
    const dependencies: FoundationDependencies = {
      chats, profiles, drafts, uuid,
      transport: (): HttpTransport => new HarmonyHttpTransport(),
      load: (): Promise<void> => this.load(chats, profiles, drafts)
    };
    this.controller = new FoundationController(dependencies);
  }
  private load(chats: ConversationRepository, profiles: ProfileRepository, drafts: DraftRepository): Promise<void> {
    if (this.loaded) return Promise.resolve();
    if (!this.loading) this.loading = (async (): Promise<void> => {
      await profiles.load(); await chats.load(); await drafts.load(); await this.ledger.load(); this.loaded = true;
    })().catch((error: Error): never => { this.loading = undefined; throw error; });
    return this.loading;
  }
  async route(want: Want): Promise<void> {
    await this.controller.initialize();
    const id = want.parameters?.runId, token = want.parameters?.taskToken;
    if (typeof id === 'string' && typeof token === 'string') {
      const task = this.ledger.validAction(id, token);
      if (task && this.controller.ports.chats.get(task.conversationId)) await this.controller.selectConversation(task.conversationId);
    }
    // Old notification action tokens may navigate to history, but never restart archived execution.
    this.controller.navigate('chat');
  }
}
