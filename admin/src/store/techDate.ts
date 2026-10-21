import { create } from "zustand";
import { todayISO } from "@/lib/format";

/** Date shared by the technician's "My Schedule" and "Route Map" pages. */
export const useTechDate = create<{ date: string; setDate: (d: string) => void }>((set) => ({
  date: todayISO(),
  setDate: (date) => set({ date: date || todayISO() }),
}));
