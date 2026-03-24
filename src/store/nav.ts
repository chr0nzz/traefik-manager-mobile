import { create } from 'zustand';

interface NavState {
  visible: boolean;
  setVisible: (v: boolean) => void;
  routeProtoFilter: string;
  setRouteProtoFilter: (f: string) => void;
}

export const useNavStore = create<NavState>((set) => ({
  visible: true,
  setVisible: (visible) => set({ visible }),
  routeProtoFilter: 'All',
  setRouteProtoFilter: (routeProtoFilter) => set({ routeProtoFilter }),
}));
