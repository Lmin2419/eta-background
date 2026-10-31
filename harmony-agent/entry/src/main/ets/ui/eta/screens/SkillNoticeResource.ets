// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/app/AgentSkillsStore.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
export function skillNoticeResource(key:string,name:string):Resource{
 switch(key){
  case 'skill_deleted_message':return $r('app.string.skill_deleted_message',name);
  case 'skill_enabled_message':return $r('app.string.skill_enabled_message',name);
  case 'state_deletion_is_not_complete_eta_will_try_to_recover_whe_c4297e':return $r('app.string.state_deletion_is_not_complete_eta_will_try_to_recover_whe_c4297e');
  case 'state_please_select_the_zip_file_provided_by_the_system_fi_fea145':return $r('app.string.state_please_select_the_zip_file_provided_by_the_system_fi_fea145');
  case 'state_replacement_confirmation_has_expired_please_select_t_fce9f2':return $r('app.string.state_replacement_confirmation_has_expired_please_select_t_fce9f2');
  case 'state_skill_has_been_deleted_34c29b':return $r('app.string.state_skill_has_been_deleted_34c29b');
  case 'state_skill_installed_b07e54':return $r('app.string.state_skill_installed_b07e54');
  case 'state_skill_pack_is_no_longer_available_please_select_the__7cdfb4':return $r('app.string.state_skill_pack_is_no_longer_available_please_select_the__7cdfb4');
  case 'state_the_built_in_skills_have_not_changed_please_try_agai_34e5e3':return $r('app.string.state_the_built_in_skills_have_not_changed_please_try_agai_34e5e3');
  case 'state_the_skill_list_is_temporarily_unavailable_please_try_29b0be':return $r('app.string.state_the_skill_list_is_temporarily_unavailable_please_try_29b0be');
  case 'state_the_skill_switch_has_not_changed_please_try_again_la_fa262f':return $r('app.string.state_the_skill_switch_has_not_changed_please_try_again_la_fa262f');
  case 'state_ui_built_in_skills_with_the_same_name_are_protected_446ad3':return $r('app.string.state_ui_built_in_skills_with_the_same_name_are_protected_446ad3');
  case 'state_ui_skill_md_is_missing_required_information_or_is_i_debe6e':return $r('app.string.state_ui_skill_md_is_missing_required_information_or_is_i_debe6e');
  case 'state_ui_skill_md_not_found_in_zip_a57975':return $r('app.string.state_ui_skill_md_not_found_in_zip_a57975');
  case 'state_ui_the_installation_failed_and_automatic_recovery_d_0d9e01':return $r('app.string.state_ui_the_installation_failed_and_automatic_recovery_d_0d9e01');
  case 'state_ui_the_local_zip_must_contain_only_one_skill_b89daf':return $r('app.string.state_ui_the_local_zip_must_contain_only_one_skill_b89daf');
  case 'state_ui_the_selected_file_cannot_be_read_please_select_a_7265b9':return $r('app.string.state_ui_the_selected_file_cannot_be_read_please_select_a_7265b9');
  case 'state_ui_the_selected_file_is_not_a_valid_zip_package_bff052':return $r('app.string.state_ui_the_selected_file_is_not_a_valid_zip_package_bff052');
  case 'state_ui_the_skill_pack_contains_an_unsafe_file_path_and__d8cfc0':return $r('app.string.state_ui_the_skill_pack_contains_an_unsafe_file_path_and__d8cfc0');
  case 'state_ui_the_skill_pack_exceeds_the_safe_size_or_file_num_42e151':return $r('app.string.state_ui_the_skill_pack_exceeds_the_safe_size_or_file_num_42e151');
  case 'state_ui_the_target_with_the_same_name_is_not_a_user_skil_00474b':return $r('app.string.state_ui_the_target_with_the_same_name_is_not_a_user_skil_00474b');
  case 'state_ui_the_zip_content_has_changed_please_reselect_and__ab8b12':return $r('app.string.state_ui_the_zip_content_has_changed_please_reselect_and__ab8b12');
  case 'state_ui_unable_to_save_skills_original_skills_have_been__9d4748':return $r('app.string.state_ui_unable_to_save_skills_original_skills_have_been__9d4748');
  case 'state_unable_to_continue_installation_136d7c':return $r('app.string.state_unable_to_continue_installation_136d7c');
  case 'state_unable_to_delete_skill_1583c9':return $r('app.string.state_unable_to_delete_skill_1583c9');
  case 'state_unable_to_install_skill_0ec70b':return $r('app.string.state_unable_to_install_skill_0ec70b');
  case 'state_unable_to_read_skill_pack_a53563':return $r('app.string.state_unable_to_read_skill_pack_a53563');
  case 'state_unable_to_read_skills_599082':return $r('app.string.state_unable_to_read_skills_599082');
  case 'state_unable_to_restore_skills_6b3d23':return $r('app.string.state_unable_to_restore_skills_6b3d23');
  case 'state_unable_to_update_skills_04e56c':return $r('app.string.state_unable_to_update_skills_04e56c');
  default:return $r('app.string.state_unable_to_install_skill_0ec70b');
 }
}
