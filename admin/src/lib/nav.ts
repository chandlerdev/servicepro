/** Sidebar navigation — mirrors the prototype's NAV / TITLES tables. */
export interface NavItem {
  href: string;
  label: string;
  /** emoji icon shown in the sidebar (as in the prototype) */
  icon: string;
  title: string;
  subtitle: string;
  /** not shown in the sidebar, but the page still works when its URL is opened directly */
  hidden?: boolean;
}

export interface NavGroup {
  /** section heading; empty string = no heading */
  label: string;
  items: NavItem[];
}

/** Admin menu, in working order: overview → daily work → master data → logs & settings. */
export const NAV_GROUPS: NavGroup[] = [
  {
    label: "Overview",
    items: [
      {
        href: "/dashboard",
        label: "Dashboard",
        icon: "🏠",
        title: "Dashboard",
        subtitle: "Today at a glance",
      },
    ],
  },
  {
    label: "Operations",
    items: [
      {
        href: "/appointments",
        label: "Schedule Appointment",
        icon: "📅",
        title: "Schedule Appointment",
        subtitle: "Collect bookings by date — exact times are allocated & texted the previous day",
      },
      {
        href: "/schedule",
        label: "Day Plan & Map",
        icon: "🗺️",
        title: "Day Plan & Map",
        subtitle: "Allocate the day's stops, optimize, view in Google Maps and notify every customer",
      },
    ],
  },
  {
    label: "Masters",
    items: [
      {
        href: "/customers",
        label: "Customers",
        icon: "👤",
        title: "Customer Master",
        subtitle: "Create, edit and delete customers",
      },
      {
        href: "/tasks",
        label: "Task Master",
        icon: "🧰",
        title: "Task Master",
        subtitle: "Service catalog with rate & time",
      },
      {
        href: "/routes",
        label: "Route Master",
        icon: "🚚",
        title: "Route Master",
        subtitle: "Vehicles, resources and route assignments",
      },
    ],
  },
  {
    label: "System",
    items: [
      {
        href: "/notifications",
        label: "Notifications",
        icon: "📨",
        title: "Notification Log",
        subtitle: "Every SMS and email sent to customers",
      },
      {
        href: "/settings",
        label: "Settings",
        icon: "⚙️",
        title: "Settings",
        subtitle: "Company profile, office depot and integrations",
      },
    ],
  },
];

/** Super Admin: manages the admins (each admin has their own technicians, customers and routes). */
export const SUPER_NAV_GROUPS: NavGroup[] = [
  {
    label: "Management",
    items: [
      {
        href: "/super",
        label: "Admins",
        icon: "🛡️",
        title: "Admins",
        subtitle: "Create and manage admins — each admin has their own technicians, customers and routes",
      },
      {
        href: "/super/cities",
        label: "Cities",
        icon: "🏙️",
        title: "City Master",
        subtitle: "One city list for every admin — their customers and routes pick from it",
        hidden: true, // reachable at /super/cities only
      },
    ],
  },
];

/** Technician view — the prototype's NAV.tech, grouped the same way as the admin menu. */
export const TECH_NAV_GROUPS: NavGroup[] = [
  {
    label: "My Work",
    items: [
      {
        href: "/tech",
        label: "My Schedule",
        icon: "🗓️",
        title: "My Schedule",
        subtitle: "Your optimized stops",
      },
      {
        href: "/tech/map",
        label: "Route Map",
        icon: "🗺️",
        title: "Route Map",
        subtitle: "Pins, route line and directions",
      },
    ],
  },
  {
    label: "System",
    items: [
      {
        href: "/tech/notifications",
        label: "Notifications",
        icon: "📨",
        title: "Notification Log",
        subtitle: "Messages sent to customers on your routes",
      },
    ],
  },
];

const ALL = [...SUPER_NAV_GROUPS, ...NAV_GROUPS, ...TECH_NAV_GROUPS].flatMap((g) => g.items);

/** Landing page for a role after login — the first item of that role's menu. */
export function homeFor(role: "SUPER_ADMIN" | "ADMIN" | "TECHNICIAN"): string {
  if (role === "SUPER_ADMIN") return "/super";
  return role === "ADMIN" ? "/dashboard" : "/tech";
}

export function isActive(href: string, pathname: string): boolean {
  if (href === "/tech" || href === "/super") return pathname === href;
  return pathname === href || pathname.startsWith(`${href}/`);
}

export function navItemFor(pathname: string): NavItem | undefined {
  return ALL.find((i) => isActive(i.href, pathname));
}
