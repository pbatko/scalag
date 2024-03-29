package com.unihogsoft.scalag.vulkan.memory;

import com.unihogsoft.scalag.vulkan.command.CommandPool;
import com.unihogsoft.scalag.vulkan.command.Fence;
import com.unihogsoft.scalag.vulkan.utility.VulkanAssertionError;
import com.unihogsoft.scalag.vulkan.utility.VulkanObjectHandle;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK10.*;

/**
 * @author MarconZet
 * Created 11.05.2019
 */
public class Buffer extends VulkanObjectHandle {
    private long allocation;
    private final int size;
    private final int usage, flags, memUsage;

    private Allocator allocator;

    public Buffer(int size, int usage, int flags, int memUsage, Allocator allocator) {
        this.allocator = allocator;
        this.size = size;
        this.usage = usage;
        this.flags = flags;
        this.memUsage = memUsage;
        create();
    }

    @Override
    protected void init() {
        try (MemoryStack stack = stackPush()) {
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.callocStack()
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .pNext(NULL)
                    .size(size)
                    .usage(usage)
                    .flags(0)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            VmaAllocationCreateInfo allocInfo = VmaAllocationCreateInfo.callocStack()
                    .usage(memUsage)
                    .requiredFlags(flags);

            LongBuffer pBuffer = stack.callocLong(1);
            PointerBuffer pAllocation = stack.callocPointer(1);
            int err = vmaCreateBuffer(allocator.get(), bufferInfo, allocInfo, pBuffer, pAllocation, null);
            if (err != VK_SUCCESS) {
                throw new VulkanAssertionError("Failed to create buffer", err);
            }
            handle = pBuffer.get();
            allocation = pAllocation.get();
        }
    }

    @Override
    protected void close() {
        vmaDestroyBuffer(allocator.get(), handle, allocation);
    }

    public void get(byte[] dst){
        int len = Math.min(dst.length, size);
        ByteBuffer byteBuffer = memCalloc(len);
        Buffer.copyBuffer(this, byteBuffer, len);
        byteBuffer.get(dst);
        memFree(byteBuffer);
    }

    public static void copyBuffer(ByteBuffer src, Buffer dst, long bytes) {
        try (MemoryStack stack = stackPush()) {
            PointerBuffer pData = stack.callocPointer(1);
            int err = vmaMapMemory(dst.getAllocator().get(), dst.getAllocation(), pData);
            if (err != VK_SUCCESS) {
                throw new VulkanAssertionError("Failed to map destination buffer memory", err);
            }
            long data = pData.get();
            memCopy(memAddress(src), data, bytes);
            vmaFlushAllocation(dst.getAllocator().get(), dst.getAllocation(), 0, bytes);
            vmaUnmapMemory(dst.getAllocator().get(), dst.getAllocation());
        }
    }

    public static void copyBuffer(Buffer src, ByteBuffer dst, long bytes) {
        try (MemoryStack stack = stackPush()) {
            PointerBuffer pData = stack.callocPointer(1);
            int err = vmaMapMemory(src.getAllocator().get(), src.getAllocation(), pData);
            if (err != VK_SUCCESS) {
                throw new VulkanAssertionError("Failed to map destination buffer memory", err);
            }
            long data = pData.get();
            memCopy(data, memAddress(dst), bytes);
            vmaUnmapMemory(src.getAllocator().get(), src.getAllocation());
        }
    }

    public static Fence copyBuffer(Buffer src, Buffer dst, long bytes, CommandPool commandPool) {
        try (MemoryStack stack = stackPush()) {
            VkCommandBuffer commandBuffer = commandPool.beginSingleTimeCommands();

            VkBufferCopy.Buffer copyRegion = VkBufferCopy.callocStack(1)
                    .srcOffset(0)
                    .dstOffset(0)
                    .size(bytes);
            vkCmdCopyBuffer(commandBuffer, src.get(), dst.get(), copyRegion);

            return commandPool.endSingleTimeCommands(commandBuffer);
        }
    }

    private Allocator getAllocator() {
        return allocator;
    }

    public int getSize() {
        return size;
    }

    private long getAllocation() {
        return allocation;
    }
}
