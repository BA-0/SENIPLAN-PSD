import { create } from "zustand";

interface MobileNavState {
  open: boolean;
  setOpen: (open: boolean) => void;
  toggle: () => void;
}

/** Etat du tiroir de navigation, utilise sous le breakpoint `lg` seulement. */
export const useMobileNavStore = create<MobileNavState>()((set) => ({
  open: false,
  setOpen: (open) => set({ open }),
  toggle: () => set((state) => ({ open: !state.open })),
}));
