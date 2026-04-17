import { create } from 'zustand';

interface DrawerState {
  isOpen: boolean;
  isWide: boolean;
  open:      () => void;
  close:     () => void;
  setIsWide: (wide: boolean) => void;
}

export const useDrawerStore = create<DrawerState>((set) => ({
  isOpen: false,
  isWide: false,
  open:      () => set({ isOpen: true }),
  close:     () => set({ isOpen: false }),
  setIsWide: (wide) => set({ isWide: wide }),
}));
