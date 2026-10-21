import { redirect } from "next/navigation";

/** Admins start on the Dashboard — the first item in their menu. */
export default function AdminHome() {
  redirect("/dashboard");
}
