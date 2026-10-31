// Harmony adapter for app/src/main/kotlin/io/github/mangi/eta/data/repository/RuntimeConfigRepository.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { RuntimePreferencesRepository } from '../core/eta/runtimePreferences';
import { CommunityCatalogRepository } from '../core/eta/communityCatalog';
import { EtaCommunityCatalog } from './EtaCommunityCatalog';
import { i18n } from '@kit.LocalizationKit';
import { PaneDateContext } from '../core/eta/conversationDrawer';
import { ExportLabels } from '../core/eta/conversationMarkdownExporter';
import { SystemNoticeCode } from '../core/eta/model/conversation';
import { EtaAppearance } from './EtaAppearance';
import { EtaConversations } from './EtaConversations';
import { bundleManager, common, Want } from '@kit.AbilityKit';
import { util } from '@kit.ArkTS';
import { deviceInfo, systemDateTime } from '@kit.BasicServicesKit';
import { display } from '@kit.ArkUI';
import { ConversationRepository } from '../core/conversations';
import { DraftRepository } from '../core/drafts';
import { ProfileRepository } from '../core/profiles';
import { TaskLedger } from '../core/runtime';
import { FoundationController, FoundationDependencies } from '../core/foundation/controller';
import { RuntimeDatabase, DatabaseConversationStorage, DatabaseDocumentStorage, DatabaseProfileStorage } from './RuntimeStorage';
import { AssetSecretStorage } from './ProfileStore';
import { HarmonyHttpTransport } from './ModelHttp';
import { HttpTransport } from '../core/model';
import { DiagnosticEnvironment, DiagnosticLog, DiagnosticTransport, emptyEnvironment } from '../core/diagnostics';

/** A small platform seam: database, secure keys and HTTP. No legacy GUI/background/extension startup. */
export class FoundationHost {
  private static instance: FoundationHost | undefined;
  static get(context: common.UIAbilityContext): FoundationHost {
    if (!FoundationHost.instance) FoundationHost.instance = new FoundationHost(context);
    return FoundationHost.instance;
  }
  readonly controller: FoundationController;
  readonly appearance:EtaAppearance;
  private loaded = false;
  private loading: Promise<void> | undefined;
  private ledger: TaskLedger;
  private runtimePreferences:RuntimePreferencesRepository;
  private context: common.UIAbilityContext;
  readonly diagnostics: DiagnosticLog;
  constructor(context: common.UIAbilityContext) {
    this.context = context;
    this.appearance=new EtaAppearance(context,()=>this.controller?.changed());
    const db = new RuntimeDatabase(context);
    const diagnostics = new DiagnosticLog(new DatabaseDocumentStorage(db, 'diagnostics'));
    this.diagnostics = diagnostics;
    const uuid = (): string => util.generateRandomUUID();
    const chats = new ConversationRepository(new DatabaseConversationStorage(db, context.filesDir), uuid, Date.now, (): void => { this.controller?.changed(); }, 2);
    const profiles = new ProfileRepository(new DatabaseProfileStorage(db, context.filesDir), new AssetSecretStorage(), uuid);
    this.runtimePreferences=new RuntimePreferencesRepository(new DatabaseDocumentStorage(db,'eta_runtime_preferences'),()=>this.controller?.changed());
    const drafts = new DraftRepository(new DatabaseDocumentStorage(db, 'drafts'));
    this.ledger = new TaskLedger(new DatabaseDocumentStorage(db, 'tasks'), uuid);
    const etaConversations=new EtaConversations(context);
    const dependencies: FoundationDependencies = {
      communityCatalog:new CommunityCatalogRepository(new EtaCommunityCatalog(context,db)),
      language:():string=>i18n.System.getAppPreferredLanguage(),
      setLanguage:(language:string):void=>{i18n.System.setAppPreferredLanguage(language);if(language!=='default')context.getApplicationContext().setLanguage(language);},
      exportName:(title:string):string=>etaConversations.fileName(title),
      paneDate:():PaneDateContext=>etaConversations.dateContext(),exportLabels:():ExportLabels=>etaConversations.labels(),
      noticeSearch:(query:string):SystemNoticeCode[]=>etaConversations.noticeSearch(query),
      exportMarkdown:(name:string,markdown:string):Promise<boolean>=>etaConversations.exportMarkdown(name,markdown),
      chats, profiles, drafts, uuid, diagnostics,runtimePreferences:this.runtimePreferences,appearance:this.appearance.repository,
      elapsed: ():number => systemDateTime.getUptime(systemDateTime.TimeType.STARTUP),
      transport: (): HttpTransport => new DiagnosticTransport(new HarmonyHttpTransport(), diagnostics),
      load: (): Promise<void> => this.load(chats, profiles, drafts),
      environment: (): Promise<DiagnosticEnvironment> => this.environment()
    };
    this.controller = new FoundationController(dependencies);
  }
  private load(chats: ConversationRepository, profiles: ProfileRepository, drafts: DraftRepository): Promise<void> {
    if (this.loaded) return Promise.resolve();
    if (!this.loading) this.loading = (async (): Promise<void> => {
      // The diagnostic log never throws; a broken log must not block the chat data.
      await this.diagnostics.load();
      await this.runtimePreferences.load(); await profiles.load(); await chats.load(); await drafts.load(); await this.ledger.load(); this.loaded = true;
    })().catch((error: Error): never => { this.loading = undefined; this.diagnostics.record('storage', '载入数据失败', error.message); throw error; });
    return this.loading;
  }
  /** Each probe is independent: one unavailable system field must not hide the others. */
  async environment(): Promise<DiagnosticEnvironment> {
    const env: DiagnosticEnvironment = emptyEnvironment();
    try {
      const info = await bundleManager.getBundleInfoForSelf(bundleManager.BundleFlag.GET_BUNDLE_INFO_DEFAULT);
      env.appVersion = info.versionName; env.versionCode = String(info.versionCode);
    } catch (e) { this.diagnostics.record('app', '读取应用版本失败', (e as Error).message); }
    try {
      env.device = deviceInfo.marketName + '（' + deviceInfo.productModel + '）';
      env.os = deviceInfo.osFullName + ' · ' + deviceInfo.displayVersion;
      env.api = String(deviceInfo.sdkApiVersion);
    } catch (e) { this.diagnostics.record('app', '读取系统信息失败', (e as Error).message); }
    try {
      const screen = display.getDefaultDisplaySync();
      env.display = screen.width + '×' + screen.height + ' px · 密度 ' + screen.densityPixels.toFixed(2);
    } catch (e) { this.diagnostics.record('app', '读取屏幕信息失败', (e as Error).message); }
    try {
      if (!display.isFoldable()) env.fold = '不可折叠';
      else {
        const status = display.getFoldStatus();
        env.fold = status === display.FoldStatus.FOLD_STATUS_EXPANDED ? '展开'
          : status === display.FoldStatus.FOLD_STATUS_FOLDED ? '折叠'
          : status === display.FoldStatus.FOLD_STATUS_HALF_FOLDED ? '半折叠' : '其他（' + status + '）';
      }
    } catch (e) { this.diagnostics.record('app', '读取折叠状态失败', (e as Error).message); }
    try {
      const scale = this.context.config.fontSizeScale;
      env.fontScale = scale === undefined ? '' : String(scale);
    } catch (e) { this.diagnostics.record('app', '读取字体缩放失败', (e as Error).message); }
    return env;
  }
  async route(want: Want): Promise<void> {
    await this.controller.initialize();
    const id = want.parameters?.runId, token = want.parameters?.taskToken;
    if (typeof id === 'string' && typeof token === 'string') {
      const task = this.ledger.validAction(id, token);
      if (task) await this.controller.notificationConversation(task.conversationId);
      return;
    }
    // Invalid or partial notification tokens do not redirect to an unrelated conversation.
    if (id !== undefined || token !== undefined) return;
    this.controller.navigate('chat');
  }
}
