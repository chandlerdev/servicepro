"use client";

import type { ReactNode } from "react";
import {
  tableFeatures,
  useTable,
  type CellData,
  type ColumnDef,
  type RowData,
  type TableFeatures,
} from "@tanstack/react-table";
import { cn } from "@/lib/utils";
import { EmptyState, ErrorState } from "./EmptyState";
import { Pagination } from "./Pagination";
import { Spinner } from "./Spinner";
import type { Page } from "@/types/api";

declare module "@tanstack/table-core" {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  interface ColumnMeta<in out TFeatures extends TableFeatures, in out TData extends RowData, TValue extends CellData = CellData> {
    /** extra classes for <th> and <td> (e.g. "text-right") */
    className?: string;
  }
}

/** Core-only feature set: sorting/filtering/pagination are done server-side. */
export const tableCoreFeatures = tableFeatures({});
export type TableCoreFeatures = typeof tableCoreFeatures;

/** Column definition type for DataTable. */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type DataColumn<T extends RowData> = ColumnDef<TableCoreFeatures, T, any>;

export interface DataTableProps<T extends RowData> {
  columns: DataColumn<T>[];
  data: T[] | undefined;
  loading?: boolean;
  error?: string | null;
  onRetry?: () => void;
  emptyText?: ReactNode;
  /** server page metadata; when provided renders pagination controls */
  page?: Pick<Page<T>, "page" | "size" | "totalElements" | "totalPages">;
  onPageChange?: (page: number) => void;
  getRowId?: (row: T) => string;
  rowClassName?: (row: T) => string | undefined;
}

const EMPTY: never[] = [];

/** TanStack Table (v9) rendered with prototype styling; server-side pagination. */
export function DataTable<T extends RowData>({
  columns,
  data,
  loading,
  error,
  onRetry,
  emptyText = "Nothing here yet.",
  page,
  onPageChange,
  getRowId,
  rowClassName,
}: DataTableProps<T>) {
  const table = useTable({
    features: tableCoreFeatures,
    columns,
    data: data ?? (EMPTY as T[]),
    getRowId: getRowId ? (row: T) => getRowId(row) : undefined,
  });

  const rows = table.getRowModel().rows;
  const initialLoading = loading && !data;

  return (
    <div>
      <div className="relative overflow-x-auto">
        <table className="dtable">
          <thead>
            {table.getHeaderGroups().map((group) => (
              <tr key={group.id}>
                {group.headers.map((header) => (
                  <th key={header.id} className={header.column.columnDef.meta?.className}>
                    {header.isPlaceholder ? null : <table.FlexRender header={header} />}
                  </th>
                ))}
              </tr>
            ))}
          </thead>
          <tbody className={cn(loading && data && "opacity-60 transition-opacity")}>
            {rows.map((row) => (
              <tr key={row.id} className={rowClassName?.(row.original)}>
                {row.getAllCells().map((cell) => (
                  <td key={cell.id} className={cell.column.columnDef.meta?.className}>
                    <table.FlexRender cell={cell} />
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
        {initialLoading && (
          <div className="flex items-center justify-center gap-2 py-10 text-muted">
            <Spinner /> Loading…
          </div>
        )}
        {!initialLoading && error && !rows.length && <ErrorState message={error} onRetry={onRetry} />}
        {!initialLoading && !error && data && rows.length === 0 && <EmptyState>{emptyText}</EmptyState>}
      </div>
      {page && onPageChange && (
        <Pagination
          page={page.page}
          size={page.size}
          totalElements={page.totalElements}
          totalPages={page.totalPages}
          onPageChange={onPageChange}
        />
      )}
    </div>
  );
}
