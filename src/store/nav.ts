import { create } from 'zustand';

interface NavState {
  routeProtoFilter: string;
  setRouteProtoFilter: (f: string) => void;
}

export const useNavStore = create<NavState>((set) => ({
  routeProtoFilter: 'All',
  setRouteProtoFilter: (routeProtoFilter) => set({ routeProtoFilter }),
}));
