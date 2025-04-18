import { IData } from "@continuum/core";
import { DataGrid, GridColDef, GridPaginationModel } from "@mui/x-data-grid";
import React, { useCallback, useEffect } from "react";
import DataService from "../../../service/DataService";

const dataService = new DataService();

export interface TableOutputViewProps {
    outputData: IData;
}

export function TableOutputView({outputData}: TableOutputViewProps) {
    const [rows, setRows] = React.useState<any[]>([]);
    const [rowsCount, setRowsCount] = React.useState<number>(0);
    const [columns, setColumns] = React.useState<GridColDef<any>[]>([]);
    const [page, setPage] = React.useState<number>(0);
    const [pageSize, setPageSize] = React.useState<number>(25);
    const [loading, setLoading] = React.useState<boolean>(true);
    
    useEffect(()=>{
        dataService.getNodeData(outputData.data, page + 1, pageSize).then((data) => {
            if(data.data.length > 0) {
                console.log(`Page: ${JSON.stringify(data)}`)
                setRowsCount(data.totalElements);
                setRows(data.data.map((row: Array<any>, idx: number) => {
                    let newRow: any = {id: idx};
                    row.forEach((cell: any) => {
                        if(cell.contentType === "text/plain") {
                            newRow[cell.name] = atob(cell.value);
                        } else {
                            newRow[cell.name] = cell.value;
                        }
                    });
                    return newRow;
                }));
                setColumns(data.data[0].map((cell: any)=>({ field: cell.name, headerName: cell.name, width: 150 })));
                setLoading(false);
            }
        });
        return () => {
            setRows([]);
            setRowsCount(0);
            setColumns([]);
            setLoading(true);
        }
    }, [outputData, page, pageSize, setRowsCount, setRows, setColumns]);

    const onPaginationModelChange = useCallback((model: GridPaginationModel)=>{
        setRows([]);
        setColumns([]);
        setPage(model.page);
        setPageSize(model.pageSize);
        setLoading(true);
    }, [setPage, setPageSize]);

    return (
        <DataGrid
            rows={rows}
            rowCount={rowsCount}
            columns={columns}
            pageSizeOptions={[5, 25, 50, 100]}
            paginationMode="server"
            paginationModel={{
                page: page,
                pageSize: pageSize
            }}
            onPaginationModelChange={onPaginationModelChange}
            loading={loading}
            sx={{
                minWidth: "500px",
            }}/>
    );
}