import { inject, provide, ref, type InjectionKey } from 'vue'

export type ProfileDialogType = 'follow' | 'fans' | null

export interface ProfileDialogState {
  dialogType: ReturnType<typeof ref<ProfileDialogType>>
  openDialog: (type: Exclude<ProfileDialogType, null>) => void
  closeDialog: () => void
}

export const profileDialogStateKey: InjectionKey<ProfileDialogState> = Symbol('profile-dialog-state')

export function useDialogState(): ProfileDialogState {
  const dialogType = ref<ProfileDialogType>(null)

  const openDialog = (type: Exclude<ProfileDialogType, null>) => {
    dialogType.value = type
  }

  const closeDialog = () => {
    dialogType.value = null
  }

  return { dialogType, openDialog, closeDialog }
}

export function provideDialogState(state: ProfileDialogState) {
  provide(profileDialogStateKey, state)
}

export function useInjectedDialogState() {
  return inject(profileDialogStateKey)
}
