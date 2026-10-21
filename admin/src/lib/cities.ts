import toast from "react-hot-toast";
import { ApiError } from "@/lib/api";
import { citiesApi } from "@/lib/services";

/**
 * "Add … as a new city" in a City dropdown: creates it in the shared list (its map position is looked up from the
 * name) and returns the name to select. Errors are thrown with a readable message for the dropdown to show.
 */
export async function addCityFromDropdown(name: string, onAdded?: () => void): Promise<string> {
  try {
    const city = await citiesApi.add(name);
    toast.success(`${city.name} added to the city list`);
    onAdded?.();
    return city.name;
  } catch (err) {
    throw new Error(err instanceof ApiError ? err.message : "Couldn't add the city — please try again");
  }
}
