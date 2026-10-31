// Ported from app/src/main/kotlin/io/github/mangi/eta/data/repository/AppearanceSettingsRepository.kt,
// ui/app/StartupSplash.kt @ 3b1794d. Harmony platform port; Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { common, ConfigurationConstant } from '@kit.AbilityKit';
import { preferences } from '@kit.ArkData';
import { window, display } from '@kit.ArkUI';
import { AppearanceSettingsRepository, AppearanceSettings, AppearanceStorage, AppearancePlatform } from '../core/eta/appearanceSettings';
class PreferenceAppearanceStorage implements AppearanceStorage {
  private context:common.UIAbilityContext;
  constructor(context:common.UIAbilityContext){this.context=context;}
  async read():Promise<string>{return (await preferences.getPreferences(this.context,{name:'eta_appearance'})).get('appearance','') as Promise<string>;}
  async write(value:string):Promise<void>{const prefs=await preferences.getPreferences(this.context,{name:'eta_appearance'});await prefs.put('appearance',value);await prefs.flush();}
}
export class EtaAppearance implements AppearancePlatform {
  readonly repository:AppearanceSettingsRepository;
  private context:common.UIAbilityContext;
  private stage:window.WindowStage|undefined=undefined;
  private contentReady:boolean=false;
  constructor(context:common.UIAbilityContext,changed:()=>void){this.context=context;this.repository=new AppearanceSettingsRepository(new PreferenceAppearanceStorage(context),this,changed);}
  async prepare(stage:window.WindowStage):Promise<void>{this.stage=stage;await this.repository.load();}
  async afterContent():Promise<void>{this.contentReady=true;await this.apply(this.repository.snapshot());}
  async apply(settings:AppearanceSettings):Promise<void>{
    const mode=settings.themeMode==='light'?ConfigurationConstant.ColorMode.COLOR_MODE_LIGHT:settings.themeMode==='dark'?ConfigurationConstant.ColorMode.COLOR_MODE_DARK:ConfigurationConstant.ColorMode.COLOR_MODE_NOT_SET;
    this.context.getApplicationContext().setColorMode(mode);
    if(this.stage&&this.contentReady){this.stage.setCustomDensity(display.getDefaultDisplaySync().densityPixels*settings.interfaceScale,true);}
    await this.refreshSystemBars(settings);
  }
  async refreshSystemBars(settings:AppearanceSettings=this.repository.snapshot()):Promise<void>{
    const dark=settings.themeMode==='dark'||settings.themeMode==='system'&&this.context.config.colorMode===ConfigurationConstant.ColorMode.COLOR_MODE_DARK;
    AppStorage.setOrCreate('darkTheme',dark);
    if(!this.stage)return;const main=await this.stage.getMainWindow();await main.setWindowLayoutFullScreen(false);
    const resources=this.context.resourceManager;
    const surface='#'+(resources.getColorSync($r('app.color.miuix_background').id)>>>0).toString(16).padStart(8,'0');
    const foreground='#'+(resources.getColorSync($r('app.color.miuix_on_background').id)>>>0).toString(16).padStart(8,'0');
    await main.setWindowSystemBarProperties({statusBarColor:surface,navigationBarColor:surface,statusBarContentColor:foreground,navigationBarContentColor:foreground});
  }
}
