export interface TransferTask {
	id: string;
	type: 'upload' | 'download';
	name: string;
	size: number;
	loaded: number;
	progress: number; // 0 - 100
	speedText: string;
	status: 'queued' | 'transferring' | 'completed' | 'cancelled' | 'error';
	errorMessage?: string;
	targetPath?: string;
	fileBlob?: File;
	url?: string;
	totalChunks?: number;
	currentChunk?: number;
	xhr?: XMLHttpRequest;
	abortFn?: () => void;
}

const CHUNK_SIZE = 2 * 1024 * 1024; // 2MB per chunk (chống nghẽn & tương thích tốt Cloudflare)
const MAX_CONCURRENT = 2; // Tối đa 2 file truyền cùng lúc

class TransferQueueStore {
	tasks = $state<TransferTask[]>([]);
	isOpen = $state(false); // Trạng thái mở popover của Floating Button

	// Đếm số task đang chạy hoặc chờ
	activeCount = $derived(
		this.tasks.filter((t) => t.status === 'queued' || t.status === 'transferring').length
	);

	// Tính tiến độ tổng thể của toàn bộ Queue (0 - 100)
	overallProgress = $derived.by(() => {
		const activeOrDone = this.tasks.filter((t) => t.status !== 'cancelled');
		if (activeOrDone.length === 0) return 0;
		const totalBytes = activeOrDone.reduce((sum, t) => sum + (t.size || 1), 0);
		const loadedBytes = activeOrDone.reduce((sum, t) => sum + (t.loaded || 0), 0);
		return Math.min(100, Math.round((loadedBytes / totalBytes) * 100));
	});

	toggleOpen() {
		this.isOpen = !this.isOpen;
	}

	setOpen(val: boolean) {
		this.isOpen = val;
	}

	// Thêm 1 hoặc nhiều file vào hàng đợi Upload
	addUploads(files: File[], targetPath: string, apiBase: string) {
		for (const file of files) {
			const id = `up_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`;
			const totalChunks = Math.ceil(file.size / CHUNK_SIZE) || 1;

			const task: TransferTask = {
				id,
				type: 'upload',
				name: file.name,
				size: file.size,
				loaded: 0,
				progress: 0,
				speedText: '0 KB/s',
				status: 'queued',
				targetPath,
				fileBlob: file,
				totalChunks,
				currentChunk: 0
			};

			this.tasks.push(task);
		}

		this.processQueue(apiBase);
	}

	// Thêm file vào hàng đợi Download
	addDownload(name: string, path: string, size: number, apiBase: string) {
		const id = `dl_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`;
		const url = `${apiBase}/storage/download?path=${encodeURIComponent(path)}`;

		const task: TransferTask = {
			id,
			type: 'download',
			name,
			size,
			loaded: 0,
			progress: 0,
			speedText: '0 KB/s',
			status: 'queued',
			url
		};

		this.tasks.push(task);
		this.processQueue(apiBase);
	}

	// Hủy 1 task
	cancelTask(id: string, apiBase: string) {
		const task = this.tasks.find((t) => t.id === id);
		if (!task) return;

		if (task.abortFn) {
			task.abortFn();
		} else if (task.xhr) {
			task.xhr.abort();
		}

		task.status = 'cancelled';
		task.speedText = 'Đã hủy';

		// Nếu là upload chunk thì báo server dọn chunk tạm
		if (task.type === 'upload') {
			fetch(`${apiBase}/storage/upload/chunk/cancel`, {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({ uploadId: task.id })
			}).catch(() => {});
		}

		this.processQueue(apiBase);
	}

	// Hủy tất cả
	cancelAll(apiBase: string) {
		for (const task of this.tasks) {
			if (task.status === 'queued' || task.status === 'transferring') {
				this.cancelTask(task.id, apiBase);
			}
		}
	}

	// Xóa các task đã xong / hủy khỏi danh sách
	clearFinished() {
		this.tasks = this.tasks.filter((t) => t.status === 'queued' || t.status === 'transferring');
	}

	// Điều phối Queue
	private processQueue(apiBase: string) {
		const activeRunning = this.tasks.filter((t) => t.status === 'transferring').length;
		if (activeRunning >= MAX_CONCURRENT) return;

		const nextTask = this.tasks.find((t) => t.status === 'queued');
		if (!nextTask) return;

		if (nextTask.type === 'upload') {
			this.runUploadChunked(nextTask, apiBase);
		} else {
			this.runDownload(nextTask, apiBase);
		}

		// Gọi đệ quy kiểm tra xem có slot chạy song song tiếp theo không
		this.processQueue(apiBase);
	}

	// Thực hiện Upload theo từng Chunk kèm Resume & Hybrid Progress
	private async runUploadChunked(task: TransferTask, apiBase: string) {
		if (!task.fileBlob) return;
		task.status = 'transferring';

		const file = task.fileBlob;
		const totalChunks = task.totalChunks || 1;
		const uploadId = task.id;

		// Kiểm tra server đã có chunk nào chưa (Resume)
		let uploadedChunkSet = new Set<number>();
		try {
			const res = await fetch(`${apiBase}/storage/upload/chunk/status?uploadId=${encodeURIComponent(uploadId)}`);
			if (res.ok) {
				const data = await res.json();
				if (Array.isArray(data.uploadedChunks)) {
					uploadedChunkSet = new Set(data.uploadedChunks);
				}
			}
		} catch {
			// bỏ qua nếu lỗi check resume
		}

		let lastLoadedTotal = 0;
		let lastTime = Date.now();

		// Duyệt từng chunk
		for (let chunkIdx = 0; chunkIdx < totalChunks; chunkIdx++) {
			if ((task.status as string) === 'cancelled') return;

			task.currentChunk = chunkIdx;

			// Nếu chunk này đã tải rồi thì bỏ qua
			if (uploadedChunkSet.has(chunkIdx)) {
				continue;
			}

			const start = chunkIdx * CHUNK_SIZE;
			const end = Math.min(start + CHUNK_SIZE, file.size);
			const chunkBlob = file.slice(start, end);

			const formData = new FormData();
			formData.append('uploadId', uploadId);
			formData.append('chunkIndex', chunkIdx.toString());
			formData.append('file', chunkBlob, file.name);

			try {
				await new Promise<void>((resolve, reject) => {
					const xhr = new XMLHttpRequest();
					task.xhr = xhr;
					task.abortFn = () => xhr.abort();

					xhr.open('POST', `${apiBase}/storage/upload/chunk`);

					xhr.upload.onprogress = (e) => {
						if ((task.status as string) === 'cancelled') return;
						const now = Date.now();

						// TÍNH TIẾN ĐỘ THEO CHUNK & DUNG LƯỢNG (HYBRID SIZE-CHUNK)
						const currentChunkLoaded = e.loaded;
						const completedBytes = chunkIdx * CHUNK_SIZE;
						const totalSentBytes = Math.min(file.size, completedBytes + currentChunkLoaded);

						task.loaded = totalSentBytes;
						task.progress = Math.min(99, Math.round((totalSentBytes / file.size) * 100));

						// Tính tốc độ
						const timeDiff = (now - lastTime) / 1000;
						if (timeDiff >= 0.4 || totalSentBytes === file.size) {
							const bytesDiff = totalSentBytes - lastLoadedTotal;
							const speed = timeDiff > 0 ? bytesDiff / timeDiff : 0;
							task.speedText = `${formatBytes(speed)}/s`;
							lastLoadedTotal = totalSentBytes;
							lastTime = now;
						}
					};

					xhr.onload = () => {
						if (xhr.status >= 200 && xhr.status < 300) {
							uploadedChunkSet.add(chunkIdx);
							resolve();
						} else {
							reject(new Error(`Chunk ${chunkIdx} thất bại (${xhr.status})`));
						}
					};

					xhr.onerror = () => reject(new Error('Lỗi kết nối khi gửi chunk'));
					xhr.onabort = () => reject(new Error('Đã hủy tải lên'));

					xhr.send(formData);
				});
			} catch (err: any) {
				if ((task.status as string) === 'cancelled') return;
				task.status = 'error';
				task.errorMessage = err.message || 'Lỗi truyền tải chunk';
				this.processQueue(apiBase);
				return;
			}
		}

		if ((task.status as string) === 'cancelled') return;

		// Tất cả chunk đã gửi xong -> Gọi lệnh MERGE CHUNKS trên Server
		task.speedText = 'Đang ghép file...';
		try {
			const mergeRes = await fetch(`${apiBase}/storage/upload/chunk/merge`, {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({
					uploadId,
					totalChunks,
					targetPath: task.targetPath || '',
					fileName: file.name
				})
			});

			const mergeData = await mergeRes.json();
			if (!mergeRes.ok || mergeData.status === 'error') {
				throw new Error(mergeData.message || 'Lỗi ghép file trên máy chủ');
			}

			task.status = 'completed';
			task.progress = 100;
			task.loaded = file.size;
			task.speedText = 'Hoàn tất';
		} catch (err: any) {
			task.status = 'error';
			task.errorMessage = err.message;
		} finally {
			task.xhr = undefined;
			task.abortFn = undefined;
			this.processQueue(apiBase);
		}
	}

	// Thực hiện Download có theo dõi % tiến độ
	private async runDownload(task: TransferTask, apiBase: string) {
		if (!task.url) return;
		task.status = 'transferring';

		let lastLoaded = 0;
		let lastTime = Date.now();

		try {
			await new Promise<void>((resolve, reject) => {
				const xhr = new XMLHttpRequest();
				task.xhr = xhr;
				task.abortFn = () => xhr.abort();

				xhr.open('GET', task.url!);
				xhr.responseType = 'blob';

				xhr.onprogress = (e) => {
					if (task.status === 'cancelled') return;
					const now = Date.now();
					task.loaded = e.loaded;

					if (e.lengthComputable && e.total > 0) {
						task.size = e.total;
						task.progress = Math.round((e.loaded / e.total) * 100);
					}

					const timeDiff = (now - lastTime) / 1000;
					if (timeDiff >= 0.4) {
						const bytesDiff = e.loaded - lastLoaded;
						const speed = timeDiff > 0 ? bytesDiff / timeDiff : 0;
						task.speedText = `${formatBytes(speed)}/s`;
						lastLoaded = e.loaded;
						lastTime = now;
					}
				};

				xhr.onload = () => {
					if (xhr.status >= 200 && xhr.status < 300) {
						const blob = xhr.response;
						const url = window.URL.createObjectURL(blob);
						const a = document.createElement('a');
						a.href = url;
						a.download = task.name;
						document.body.appendChild(a);
						a.click();
						document.body.removeChild(a);
						window.URL.revokeObjectURL(url);

						task.status = 'completed';
						task.progress = 100;
						task.speedText = 'Hoàn tất';
						resolve();
					} else {
						reject(new Error(`Tải về thất bại: HTTP ${xhr.status}`));
					}
				};

				xhr.onerror = () => reject(new Error('Lỗi kết nối khi tải file'));
				xhr.onabort = () => reject(new Error('Đã hủy tải xuống'));

				xhr.send();
			});
		} catch (err: any) {
			if ((task.status as string) === 'cancelled') return;
			task.status = 'error';
			task.errorMessage = err.message;
		} finally {
			task.xhr = undefined;
			task.abortFn = undefined;
			this.processQueue(apiBase);
		}
	}
}

export function formatBytes(bytes: number, decimals = 1): string {
	if (!bytes || bytes === 0) return '0 B';
	const k = 1024;
	const dm = decimals < 0 ? 0 : decimals;
	const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
	const i = Math.floor(Math.log(bytes) / Math.log(k));
	return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
}

export const transferQueue = new TransferQueueStore();
