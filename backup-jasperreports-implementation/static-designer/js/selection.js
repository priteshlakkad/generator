const Selection = (() => {

	function startMove(downEvent, el, zoom, rerender) {
		const startX = downEvent.clientX;
		const startY = downEvent.clientY;
		const originX = el.x;
		const originY = el.y;

		function onMove(e) {
			const dx = Math.round((e.clientX - startX) / zoom);
			const dy = Math.round((e.clientY - startY) / zoom);
			el.x = Math.max(0, originX + dx);
			el.y = Math.max(0, originY + dy);
			rerender();
		}

		function onUp() {
			document.removeEventListener('mousemove', onMove);
			document.removeEventListener('mouseup', onUp);
		}

		document.addEventListener('mousemove', onMove);
		document.addEventListener('mouseup', onUp);
	}

	function startResize(downEvent, el, corner, zoom, rerender) {
		const startX = downEvent.clientX;
		const startY = downEvent.clientY;
		const origin = { x: el.x, y: el.y, width: el.width, height: el.height };

		function onMove(e) {
			const dx = Math.round((e.clientX - startX) / zoom);
			const dy = Math.round((e.clientY - startY) / zoom);

			if (corner.includes('e')) {
				el.width = Math.max(4, origin.width + dx);
			}
			if (corner.includes('s')) {
				el.height = Math.max(4, origin.height + dy);
			}
			if (corner.includes('w')) {
				const newWidth = Math.max(4, origin.width - dx);
				el.x = origin.x + (origin.width - newWidth);
				el.width = newWidth;
			}
			if (corner.includes('n')) {
				const newHeight = Math.max(4, origin.height - dy);
				el.y = origin.y + (origin.height - newHeight);
				el.height = newHeight;
			}
			rerender();
		}

		function onUp() {
			document.removeEventListener('mousemove', onMove);
			document.removeEventListener('mouseup', onUp);
		}

		document.addEventListener('mousemove', onMove);
		document.addEventListener('mouseup', onUp);
	}

	return { startMove, startResize };
})();
