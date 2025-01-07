export default interface ITreeItem<T> {
    id: string;
    name: string;
    itemInfo?: T;
    children?: ITreeItem<T>[];
}