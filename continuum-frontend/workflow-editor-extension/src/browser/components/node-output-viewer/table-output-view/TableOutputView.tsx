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
    
    useEffect(()=>{
        dataService.getNodeData(outputData.data, page + 1, pageSize).then((data) => {
            if(data.rows.length > 0) {
                console.log(`Page: ${JSON.stringify(data)}`)
                setRowsCount(data.metadata.total);
                setRows(data.rows.map((row, idx)=>({id:idx, ...row})));
                setColumns(Object.entries(data.rows[0]).map(([key])=>({ field: key, headerName: key, width: 150 })));
            }
        });
    }, [outputData, page, pageSize, setRowsCount, setRows, setColumns]);

    const onPaginationModelChange = useCallback((model: GridPaginationModel)=>{
        setPage(model.page);
        setPageSize(model.pageSize);
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
            sx={{
                minWidth: "500px",
            }}/>
    );
}