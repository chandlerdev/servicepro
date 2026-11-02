import Image from "next/image";
import logo from "@/assets/logo.png";
import { cn } from "@/lib/utils";

/** The SPW logo on a white rounded tile (works on the dark sidebar and on light pages). */
export function BrandLogo({ size = 40, className }: { size?: number; className?: string }) {
  return (
    <span
      className={cn("inline-flex shrink-0 items-center justify-center rounded-[10px] bg-white shadow-sm", className)}
      style={{ width: size, height: size, padding: Math.round(size * 0.1) }}
    >
      <Image src={logo} alt="ServProWare" width={size} height={size} className="h-full w-auto" priority />
    </span>
  );
}
